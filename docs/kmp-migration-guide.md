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

この制約を解消するため、将来的な KMP 移行時には以下の永続化機構を組み込みます。

### 永続化ライブラリの利用設計
Android では、権限要求済みフラグの保存に Jetpack DataStore Preferences を利用します。DataStore は suspend / Flow ベースで扱えるため、Repository と UseCase の境界も suspend API として定義します。

KMP 共通モジュールでキーバリュー型永続化を扱う場合は、**[multiplatform-settings](https://github.com/russhwolf/multiplatform-settings)** または KMP 対応 DataStore Preferences の導入を検討します。

- **Android 側**: DataStore Preferences にマッピング
- **iOS 側**: `NSUserDefaults` 等にマッピング

### 永続化を用いた状態検知フロー（共通ロジック化）

`NetworkUseCase` は、コルーチンローカルの `previousStatus` 変数のフォールバックとして、プラットフォームごとに注入される Key-Value 永続化ストア（`Settings` インターフェース）を参照し、バックグラウンド起動時にも正しく WiFi → モバイル の状態遷移を検知できるように設計します。

UseCase は値や `Job` を返さず、Presenter 経由で外側へ通知します。監視 coroutine の起動と `Job` 管理は、Android では `ForegroundMonitoringService`、iOS では `BackgroundMonitoringServiceImpl` などの Platform 側が担当します。

#### 状態チェックの疑似コード (共通ロジック):
```kotlin
class NetworkUseCase(
    private val networkConnectivity: NetworkConnectivity,
    private val settings: Settings // KMP 共通のキーバリュー永続化
) {
    companion object {
        private const val KEY_LAST_KNOWN_STATUS = "last_known_network_status"
    }

    suspend fun observe(
        notificationPresenter: NetworkNotificationPresenter,
        statusPresenter: NetworkStatusPresenter,
    ) {
        var previousStatus: NetworkStatus? = getLastKnownStatus()  // 永続化から復元

        networkConnectivity.observeNetworkStatus().collect { result ->
            val current = result.getOrNull() ?: return@collect

            // WiFi → Mobile への切り替え検知
            if (previousStatus is NetworkStatus.Connected &&
                (previousStatus as NetworkStatus.Connected).type == NetworkStatus.NetworkType.Wifi &&
                current is NetworkStatus.Connected &&
                current.type == NetworkStatus.NetworkType.Mobile
            ) {
                notificationPresenter.displayNotification()
            }

            // 永続化ストアに現在の接続情報を保存
            saveStatus(current)
            previousStatus = current

            statusPresenter.onNetworkStatusUpdated(result.toMonitoringStatus())
        }
    }

    private fun Result<NetworkStatus>.toMonitoringStatus(): NetworkMonitoringStatus =
        fold(
            onSuccess = { status -> NetworkMonitoringStatus.Available(status) },
            onFailure = { NetworkMonitoringStatus.Failed },
        )

    private fun getLastKnownStatus(): NetworkStatus? {
        val typeString = settings.getStringOrNull(KEY_LAST_KNOWN_STATUS) ?: return null
        return when (typeString) {
            "WIFI" -> NetworkStatus.Connected(NetworkStatus.NetworkType.Wifi)
            "MOBILE" -> NetworkStatus.Connected(NetworkStatus.NetworkType.Mobile)
            "OTHER" -> NetworkStatus.Connected(NetworkStatus.NetworkType.Other)
            else -> NetworkStatus.NotConnected
        }
    }

    private fun saveStatus(status: NetworkStatus) {
        val value = when (status) {
            is NetworkStatus.Connected -> when (status.type) {
                NetworkStatus.NetworkType.Wifi -> "WIFI"
                NetworkStatus.NetworkType.Mobile -> "MOBILE"
                NetworkStatus.NetworkType.Other -> "OTHER"
            }
            is NetworkStatus.NotConnected -> "NOT_CONNECTED"
        }
        settings.putString(KEY_LAST_KNOWN_STATUS, value)
    }
}
```

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
- [ ] **フェーズ 2: パッケージ整理と共有モジュール (shared) の新設**
  - [ ] プラットフォーム非依存コードを `domain/{model,usecase,gateway}` に再配置し、状態保持(`monitor`)・`viewmodel`・`ui` をネイティブ層へ分離
  - [ ] `shared` マルチプラットフォームモジュールを Gradle に作成（当面 androidTarget のみ。iOS は phase 4）
  - [ ] `domain/`（`model` / `usecase` / `gateway`）を `commonMain` へ移動
  - [ ] `monitor`(`NetworkMonitor`)・`viewmodel`(`NetworkViewModel` / `NetworkUiStatus` / `NetworkUiEffect`)・`ui`・`platform` 実装は `:app`（Android ネイティブ）に残置
- [ ] **フェーズ 3: 状態永続化の共通化**
  - [ ] `multiplatform-settings` の依存追加
  - [ ] `NetworkUseCase.observe()` の `previousStatus` 初期値を `Settings` ストアから復元する形に拡張
- [ ] **フェーズ 4: iOS プラットフォーム実装の追加**
  - [ ] iOS `iosMain` において `NWPathMonitor` を用いた `NetworkConnectivityImpl` を実装
  - [ ] iOS 用 `BackgroundMonitoringServiceImpl` にて `BGTaskScheduler` と `UserDefaults` の統合を実装（`NetworkNotificationPresenter` も実装）
