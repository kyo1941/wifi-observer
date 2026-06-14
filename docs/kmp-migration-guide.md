# KMP 移行ロードマップ & プラットフォーム設計ガイド

本ドキュメントは、現在 Android ファーストで作成しているネットワーク監視機能を将来的に Kotlin Multiplatform (KMP) に移行する際の、モジュール構造、パッケージ設計、およびプラットフォーム固有機能の抽象化指針をまとめた仕様書です。

---

## 1. モジュール構成とファイル配置予定図

KMP 移行では、プラットフォーム非依存のコアを `domain/` パッケージに集約し、これをそのまま `shared` モジュールの `commonMain` へ抽出します。状態保持（`StateFlow`）・画面・ViewModel は各プラットフォームのネイティブ層に残します（理由は 1.1）。

```text
【共通コア domain/（→ shared/commonMain へ抽出予定）】
com/example/wifi_observer/
├── domain/
│   ├── model/
│   │   ├── NetworkStatus.kt                      (WiFi/Mobile 定義)
│   │   ├── NetworkMonitoringStatus.kt            (監視結果モデル)
│   │   ├── NotificationPermissionStatus.kt       (通知許可状態)
│   │   └── NotificationPermissionRequestResult.kt
│   ├── usecase/
│   │   ├── NetworkUseCase.kt                      (★状態遷移検知のコアロジック)
│   │   └── NotificationPermissionUseCase.kt       (通知許可判定・UI 要求)
│   └── gateway/                                   (コアが外界と結ぶ契約 = ポート群)
│       ├── NetworkConnectivity.kt                (接続状態の観測 I/F)
│       ├── NetworkNotifier.kt                    (通知発火 I/F)
│       ├── NetworkNotificationPresenter.kt       (通知発火の出力ポート)
│       ├── NetworkStatusPresenter.kt             (状態更新の出力ポート)
│       ├── NotificationPermissionPresenter.kt    (通知許可 UI 要求の出力ポート)
│       ├── NotificationPermissionRepository.kt   (通知許可状態 Repository)
│       └── BackgroundMonitoringService.kt        (監視開始・停止 I/F)

【Android ネイティブ層（現状 :app。gateway 実装・状態保持・UI）】
com/example/wifi_observer/
├── platform/                                     (gateway の Android 実装 = アダプタ)
│   ├── NetworkConnectivityImpl.kt                (ConnectivityManager)
│   ├── NetworkNotifierImpl.kt                    (NotificationManager)
│   ├── NotificationPermissionRepositoryImpl.kt   (POST_NOTIFICATIONS + DataStore)
│   ├── NotificationPermissionRequestResultMapper.kt
│   ├── ForegroundMonitoringService.kt            (FGS wrapper・監視 Job 管理)
│   └── ForegroundMonitoringServiceController.kt  (BackgroundMonitoringService 実装)
├── monitor/
│   └── NetworkMonitor.kt                          (Facade。StateFlow で状態公開・Job 管理)
├── viewmodel/
│   ├── NetworkViewModel.kt                        (NetworkMonitor.status を UI 状態へ変換)
│   ├── NetworkUiStatus.kt                         (UI モデル)
│   ├── NetworkUiEffect.kt                         (単発 UI イベント)
│   └── factory/NetworkViewModelFactory.kt
├── ui/                                            (Jetpack Compose)
│   ├── MainActivity.kt
│   ├── theme/   components/network/
│   └── NetworkScreen.kt / NetworkContentView.kt / NetworkActionLayout.kt / NetworkInitialView.kt
├── di/AppContainer.kt
└── WifiObserverApplication.kt

【iOS ネイティブ層（将来 phase 4）】
- platform（iosMain）: NWPathMonitor 版 NetworkConnectivityImpl、
  UNUserNotificationCenter 版 NetworkNotifierImpl、
  BGTaskScheduler + UserDefaults 統合の BackgroundMonitoringService 実装
- 状態保持 Facade（NetworkMonitor 相当）と ViewModel は Swift でネイティブ実装
```

### 1.1 なぜ NetworkMonitor / ViewModel を共通化しないか

`NetworkMonitor` は監視結果を `status: StateFlow` として公開する Facade だが、Kotlin の `Flow` / `StateFlow` は Swift / Objective-C から直接購読できず、ブリッジ層（SKIE 等）を要する。`NetworkMonitor` はこの「Flow を境界に露出する」唯一の場所であり、共通化するとこの問題に直撃する。

一方、WiFi→モバイル検知などの本質的な業務ロジックは `NetworkUseCase`（＝ `domain`）に集約済みで、`NetworkMonitor` の役割は「push（Presenter）→ pull（StateFlow）の橋渡し」と `Job` 管理のみ、すなわち状態保持＝プレゼンテーションの都合に過ぎない。

したがって状態保持・ViewModel・UI は各プラットフォームのネイティブ層に置き、共通化は `domain/`（model・usecase・gateway）に限定する。なお `gateway` が返す `Flow`（例: `NetworkConnectivity.observeNetworkStatus()`）は `NetworkUseCase` 内部で collect されるだけで境界を越えないため、この制約には当たらない。

---

## 2. 状態の永続化とプラットフォーム間の差異の埋め方

iOS では、バックグラウンドでのリアルタイム監視が制限されているため、`BGTaskScheduler` による定期評価（バッチ処理）の際に**「前回の状態」**を保持しておく必要があります。しかし、アプリのプロセスはタスク起動の都度新しく立ち上がるため、`observe()` コルーチンローカルの `previousStatus` 変数では状態を維持できません。

### 責務の所在：永続化は iOS gateway の責務であり、common には持ち込まない

重要なのは、この永続化が必要なのは **iOS のバッチ監視モデルだけ** だという点である。Android は Foreground Service が `observe()` コルーチンを生かし続けるため、`previousStatus`（実装上は `lastConnectedType`）はコルーチンローカル変数のままで保持される。FGS が死んだ区間はそもそもリアルタイム監視が成立しないため、その間の Wifi → モバイル切り替えは永続化の有無に関わらず観測できない。よって **Android 側に永続化は不要** である。

一方で、Wifi → モバイルの**検知ロジックそのものは `NetworkUseCase`（common）に集約したまま動かさない**（1.1 節）。検知を Swift / `iosMain` に再実装すると KMP 化の意義が失われる。

この 2 つを両立させる設計は、**「前回状態の復元」を iOS の `NetworkConnectivity` 実装（gateway の iOS アダプタ）の内部詳細として閉じ込める**ことである。`NetworkUseCase` には永続化ストアを注入しない（ガイド初期案の `Settings` 直接注入は採らない）。

- **Android 側**: 永続化なし。FGS 稼働中はコルーチンローカル変数が状態の源泉。
- **iOS 側**: `NetworkConnectivityImpl`（`iosMain`）が `NSUserDefaults` に前回種別を保存・復元する。

### 永続化を用いた状態検知フロー（iOS gateway による前回状態の replay）

`NetworkUseCase` は **変更しない**。`NetworkConnectivity.observeNetworkStatus()` が返す `Flow` の**先頭に「前回状態」を流し、続けて「現在状態」を流す**ことで、既存の検知ロジックがそのまま `[前回, 現在]` の遷移として Wifi → モバイルを判定する。

iOS の `NetworkConnectivityImpl`（`iosMain`、phase 4）の責務：

1. `NSUserDefaults` から前回の接続種別を読み込み、最初に `NetworkStatus.Connected(前回種別)` を emit する（未保存なら省略）。
2. 現在のネットワーク状態を取得して emit する。
3. 現在の接続種別を `NSUserDefaults` に保存する（次回のバッチ起動に備える）。

この設計の要点：

- **common（`NetworkUseCase` / `domain`）は一切変更不要**。永続化ストアの注入も、Android 用のダミー実装も不要。
- 永続化は完全に iOS platform（gateway 実装）の内部詳細に閉じる。
- `NetworkUseCaseTest` は既に `[wifi, mobile]` のようなシーケンスを `FakeNetworkConnectivity.emit` で流して検知を検証しており、「前回 replay」はこの既存テストパターンそのもの。common 側に追加実装・追加テストは要らない。

UseCase は値や `Job` を返さず、Presenter 経由で外側へ通知する点は不変。監視 coroutine の起動と `Job` 管理は、Android では `ForegroundMonitoringService`、iOS では `BackgroundMonitoringServiceImpl` などの Platform 側が担当する。

> NOTE: replay した「前回状態」も `statusPresenter.onNetworkStatusUpdated()` に渡る。iOS のバッチ起動時にはライブな UI が無いため実害はないが、iOS の `NetworkStatusPresenter` 実装はこの先頭 emission を UI 反映対象として扱わない想定。詳細は phase 4 で確定する。

---

## 3. DI (依存関係の注入) 設計

KMP 環境下では、プラットフォーム固有の具象実装を共通モジュール側に安全に渡すため、プラットフォーム固有の Application クラス（または EntryPoint）で具象クラスを生成し、共通モジュールの DI コンテナに流し込みます。

### Android 側での初期化例 (WifiObserverApplication)
```kotlin
class WifiObserverApplication : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()

        // Android 固有の Context 等を渡して DI コンテナを初期化
        appContainer = AppContainer(context = this)
    }
}

class AppContainer(context: Context) {
    private val networkUseCase = NetworkUseCase(
        networkConnectivity = NetworkConnectivityImpl(
            context.getSystemService(ConnectivityManager::class.java)
        )
    )
    private val networkNotifier = NetworkNotifierImpl(context)
    private val notificationPermissionUseCase = NotificationPermissionUseCase(
        notificationPermissionRepository = NotificationPermissionRepositoryImpl(context)
    )
    private val backgroundMonitoringService = ForegroundMonitoringServiceController(context)

    val networkMonitor = NetworkMonitor(
        networkUseCase = networkUseCase,
        networkNotifier = networkNotifier,
        backgroundMonitoringService = backgroundMonitoringService,
    )
}
```

### iOS 側での初期化例 (Swift)
```swift
@main
struct WifiObserverApp: App {
    let appContainer: AppContainer

    init() {
        // iOS 固有の監視実装を渡して KMP の AppContainer を初期化
        self.appContainer = AppContainer(
            networkConnectivity: iOSNetworkConnectivityImpl()
        )

        // バックグラウンドタスクの初期登録
        appContainer.backgroundMonitoringService.start()
    }
}
```

---

## 4. KMP 移行フェーズチェックリスト

実装・移行を安全に進めるために、本仕様に基づいた段階的アプローチを定義します。

- [x] **フェーズ 1: Android 側での実装の完了**
  - [x] 設計書を Presenter パターン（2つのPresenterインターフェース）に基づき更新
  - [x] `NetworkNotificationPresenter` / `NetworkStatusPresenter` / `BackgroundMonitoringService` の Android 定義
  - [x] `NetworkMonitor`（`NetworkNotificationPresenter` / `NetworkStatusPresenter` 実装）による通知発火・UI状態更新の完了
  - [x] `NotificationPermissionUseCase` / `NotificationPermissionRepository` による通知許可状態判定と DataStore 永続化の完了
  - [x] `ForegroundMonitoringService` による FGS 起動、監視 coroutine の `Job` 管理、`POST_NOTIFICATIONS` 権限対応の完了
  - [x] `NetworkViewModel` による `NetworkMonitor.status` の UI 状態変換の完了
- [x] **フェーズ 2: パッケージ整理と共有モジュール (shared) の新設**
  - [x] プラットフォーム非依存コードを `domain/{model,usecase,gateway}` に再配置し、状態保持(`monitor`)・`viewmodel`・`ui` をネイティブ層へ分離
  - [x] `shared` マルチプラットフォームモジュールを Gradle に作成（当面 androidTarget のみ。iOS は phase 4）
  - [x] `domain/`（`model` / `usecase` / `gateway`）を `commonMain` へ移動
  - [x] `monitor`(`NetworkMonitor`)・`viewmodel`(`NetworkViewModel` / `NetworkUiStatus` / `NetworkUiEffect`)・`ui`・`platform` 実装は `:app`（Android ネイティブ）に残置
- [x] **フェーズ 3: 永続化の責務確定（設計の訂正）**
  - [x] 永続化は iOS のバッチ監視モデル固有の要件であり、Android（FGS 稼働）には不要であることを確認
  - [x] 検知ロジックは `NetworkUseCase`（common）に集約したまま動かさず、永続化を common に持ち込まない方針を確定
  - [x] 「前回状態の復元」は iOS `NetworkConnectivityImpl` が `Flow` 先頭に前回状態を replay する形で gateway 内部に閉じる設計に決定（2 節を改訂）。phase 3 ではコード変更なし
  - [x] ガイド初期案（`NetworkUseCase` への `Settings` 直接注入・`multiplatform-settings` 導入）は不採用とする
- [ ] **フェーズ 4: iOS プラットフォーム実装の追加**
  - [ ] iOS `iosMain` において `NWPathMonitor` を用いた `NetworkConnectivityImpl` を実装
  - [ ] 上記 `NetworkConnectivityImpl` に `NSUserDefaults` 永続化を内包し、バッチ起動時に前回の接続種別を `Flow` 先頭へ replay → 現在状態 emit → 現在種別を保存（2 節の設計）
  - [ ] iOS 用 `BackgroundMonitoringServiceImpl` にて `BGTaskScheduler` と `UserDefaults` の統合を実装（`NetworkNotificationPresenter` も実装）
