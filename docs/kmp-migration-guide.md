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

【iOS ネイティブ層（phase 4）】
- platform（iosMain）: NWPathMonitor 版 NetworkConnectivityImpl、
  UNUserNotificationCenter 版 NetworkNotifierImpl、
  BGTaskScheduler + UserDefaults 統合の BackgroundMonitoringService 実装、
  UNUserNotificationCenter 版 NotificationPermissionRepositoryImpl
- ViewModel と View は Swift でネイティブ実装。NetworkMonitor 相当の Facade は iOS では作らない
  （フォアグラウンドでは ViewModel が Presenter を直接実装できるため。2 節末尾を参照）
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

> NOTE: replay した「前回状態」も `statusPresenter.onNetworkStatusUpdated()` に渡る。かつてはこの replay がフォアグラウンド起動時（ライブ監視）にも走り、①UIのちらつき、②長時間オフライン後の古い遷移の誤通知、を起こしうる点が課題だったが、`NetworkConnectivityImpl`（iosMain）のコンストラクタ引数 `isBatchLaunch: Boolean` により解決済み（`isBatchLaunch = true` のバッチ起動時にのみ replay する。フォアグラウンド用途では `isBatchLaunch = false` を渡し、replay せず Android と同様に継続監視する）。呼び出し元（Swift 側 `AppContainer`/`BackgroundMonitoringServiceImpl`）がどちらの文脈かを知っているため、コンストラクタで明示的に渡す設計とした。バッチ起動時の `statusPresenter` は no-op であるため（2 節末尾）、この先頭 emission が UI に反映されることはない。

### `NetworkUseCase` は「同一プロセス内の継続した系列」だけを扱う

`NetworkUseCase.observe()` は元々「1回の呼び出し（＝1つの継続したプロセス実行）の中で流れてくる値の系列を見て、経過時間を管理し通知を判断する」という責務であり、これは今回のiOS対応でも一切変わらない。「バッチ内/バッチ外」という区分自体はcommonの語彙には存在せず、`NetworkUseCase`から見れば常に「いつも通りの1回の継続実行」でしかない。iOSだけが抱える「実行のたびにプロセスが生成・破棄される」という事情は、`NetworkUseCase`を呼び出す**前に** `NetworkConnectivityImpl`（iOS gateway）が責任を持って後始末しておくべきものであり、commonに一切漏らさない。

この原則に基づき、②の長時間オフライン後の誤通知は次のように解決した（Androidの5秒grace = 継続監視中に発生する技術的アーティファクトの吸収、とは全く別の概念であることに注意）：

- この永続化は `PreviousNetworkTypeStore`（iosMain）が担い、`NSUserDefaults` のキーを単独で所有する。読み書きするのは `NetworkConnectivityImpl`（観測のたびに保存・無効化）と `BackgroundMonitoringServiceImpl`（監視の停止時に無効化）の2箇所で、どちらもこの store 越しにのみ触る
- `PreviousNetworkTypeStore` は接続種別を保存する際、保存時刻（epoch秒）も併せて保存する
- **`NotConnected` を観測した場合は、保存済みの接続種別を即座に無効化する**（主たる防御）。切断が確認された以上、それより前の接続種別をreplayに使うと確認済みの切断期間を無視してしまうため
- **監視を停止した場合も同様に無効化する**。停止中の切り替えは誰も観測していないため、再開後の最初のバッチが停止前の種別をreplayすると、監視していなかった区間で起きた切り替えを「たった今の切り替え」として通知してしまう（WiFiで停止 → 停止中にMobileへ切り替え → 15分以内に再開、で発火する。PRレビュー指摘）。「新しい監視セッションは前のセッションの最後の値を前提にしない」という形で、上の `NotConnected` と同じ原則に揃えている
- `isBatchLaunch = true` でのreplay判断時、保存時刻が一定の閾値より古ければ、**replayそのものを行わない**（`NetworkUseCase`は`lastConnectedType=null`から始まり、結果的に通知は発火しない）。この閾値が実際に効くのは「切断も停止も起きないまま時間が経過した」場合のみの保険的な位置づけ
  - 閾値は**`BGTaskScheduler`の実起動間隔に合わせて決めるものではない**。実起動間隔はOSの裁量による機会主義的なもので15分〜数時間、それ以上（あるいは実行なし）もありうるため、これに閾値を合わせようとすると「何時間も前の出来事を"たった今"として通知する」ことを許容してしまい、通知自体の意味が失われる
  - 代わりに「これより古い情報を通知に使うのは無意味」というアプリ側の基準（15分）を閾値とし、OSがこの時間内に起動しなければ検知を諦める（通知しない）という意図的なトレードオフを採る（PRレビューで最初に60秒→24時間と提案したが、いずれも上記の理由で不適切と判断し15分に変更した経緯がある）
- 保存(接続種別・保存時刻とも)は `isBatchLaunch` に関わらず常に行う

また、`NWPathMonitor` は起動直後に確定していない値を複数回連続して返すことがある（Apple Developer Forumsでも既知の挙動として報告されている）ため、`isBatchLaunch = true` の場合はNWPathMonitorの発火が一定時間（デバウンス窓）静止するまで待ち、最後に観測した値を確定値として扱ってから `Flow` を完了させる。これも`NetworkConnectivityImpl`内部だけで完結し、`NetworkUseCase`には一切影響しない。

### iOS の Presenter 配線と FG/BG 切り替え（phase 4 タスク1 での設計確定事項）

iOS では Presenter の実装が実行文脈ごとに2つに分かれる。判断基準は「ViewModel が Present された値を拾って状態を更新できるか」であり、Android で Monitor 層を噛ませた動機と同じ問題への iOS 版の答えである。

- **フォアグラウンド**: Swift の ViewModel が `NetworkStatusPresenter` / `NetworkNotificationPresenter` を実装し、`isBatchLaunch = false` で `observe()` を回す。ViewModel が直接拾えるため、`NetworkMonitor` 相当の中間 Facade は作らない。
- **バックグラウンド（`BGTaskScheduler` バッチ）**: OS はプロセスと `App.init()` を起こすが UI シーンを接続しないため、View も ViewModel も生成されない。よって `BackgroundMonitoringServiceImpl`（iosMain）自身が `NetworkNotificationPresenter` を実装し、通知発火は `NetworkNotifier` に委譲する。`statusPresenter` には no-op 実装を渡す — バッチのプロセスは UseCase 完了後に破棄されるため、Present された状態の更新先（UI・メモリ・鮮度の観点で意味のある永続化先）が構造的に存在しない。初期画面の表示は、Android では Monitor のメモリ保持が担っていた役割を、iOS ではフォアグラウンド起動時の観測の即時性（NWPathMonitor が監視開始直後に現在状態を発火する）が担う。

`BackgroundMonitoringServiceImpl` の公開 API は共通 interface の `start()`/`stop()` に加え、interface 外の `register()` を持つ:

- `register()`: `BGTaskScheduler` へのハンドラ登録。アプリ起動完了前に毎回呼ぶ必要がある（遅れると OS のタスク起動時にクラッシュする）ため、Swift の App 初期化から毎起動時に呼ぶ。「監視開始」とは無関係の起動時儀式であり、Android には存在しない概念のため共通 interface には足さない（iOS 固有の事情を common に漏らさない）。
- `start()` = `submitTaskRequest`（予約1件）。`BGAppRefreshTaskRequest` の実行は一回きりのため、launchHandler 内で observe 完了後に次の予約を再投入する。この連鎖は impl 内部に閉じ、Swift 側は再スケジュールを意識しない。予約はユーザーが Background App Refresh を無効化している等の理由で拒否されうるため、成立を確認してから監視中フラグを立てる。再投入が拒否された場合は連鎖が途切れて以後の検知が走らないため、逆にフラグを降ろす。
- `stop()` は4つを行う。①`cancelTaskRequestWithIdentifier` で保留中の予約を取り消す ②実行中のバッチ（`observeJob`）を cancel する — 取り消せるのは保留中の予約だけで、実行中のものはそのまま検知・通知しうるため ③監視中フラグを降ろす ④次回の遷移判定に使う基準値（`PreviousNetworkTypeStore`）を捨てる — 停止中の切り替えは観測できないため（2 節を参照）。この「監視中フラグ」は `NSUserDefaults` の1キーで、再投入の抑止判定と FG 復帰時の再構成判定の両方が同じ値を見る。
  - ②③と launchHandler 側の「フラグ確認 → Job 設置」は `NSLock` で排他する。`stop()` は UI から、launchHandler は `BGTaskScheduler` のキューから呼ばれるため、確認と設置の間に割り込まれると停止後にバッチが起動しうる。

FG↔BG の切り替え追従は「中断状態を作らない」方針で担保する:

- BG へ移るたびにフォアグラウンドの `observe()` を cancel する。suspend で凍結したコルーチンを残すと、コルーチンローカルの `lastConnectedType` が古いまま復帰時の再発火と噛み合い、何時間も前の Wifi→Mobile を「たった今」として二重通知するため（上記「同一プロセス内の継続した系列」の前提が suspend で破れる）。
- FG 復帰時は監視中フラグ（`stop()` の項と同一キー）を読み、稼働中なら `observe()` を新規起動、待機中なら待機画面で再構成する。フラグ未設定（初回起動・再インストール等）は待機側に倒す。バッチ連鎖の再投入判定も同じフラグを見るため、両経路の稼働/停止がずれない。なお shared 側の宣言には「何ができるか」だけを書き、アプリ上でどう使うかは呼び出し側である Swift のコードにコメントする。具体的には「`isMonitoring` を FG 復帰時の再構成判断に使う」ことも「`register()` を監視の開始状態に関わらず毎回のアプリ起動時に呼ぶ」ことも Swift 側に書く（shared は iOS アプリ専用ではなく、使われ方を知らない API として保つため）。
- 結果として BG 中の検知はバッチ連鎖、FG の検知は「その滞在中に始まった系列」と責務が分かれ、common（`NetworkUseCase`）には一切手を入れない。

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
  - [x] iOS `iosMain` において `NWPathMonitor`（`platform.Network` の C API）を用いた `NetworkConnectivityImpl` を実装（`shared/src/iosMain/kotlin/com/example/wifi_observer/platform/NetworkConnectivityImpl.kt`）
  - [x] 上記 `NetworkConnectivityImpl` に `NSUserDefaults` 永続化を内包し、バッチ起動時に前回の接続種別を `Flow` 先頭へ replay → 現在状態 emit → 現在種別を保存（2 節の設計）。前回状態の replay をバッチ起動時に限定する既知の課題は、コンストラクタ引数 `isBatchLaunch: Boolean` の DI フラグで解決した（`isBatchLaunch = true` のときのみ replay し、現在値を1件受け取った時点で `Flow` を完了させて `BGTaskScheduler` の実行時間制約に収める。保存自体は `isBatchLaunch` に関わらず常に行う）
  - [x] 長時間オフライン後にreplayされた古い前回状態で誤通知が発生する課題（PRレビュー指摘）を解決。`NotConnected` 観測時に保存済み接続種別を即座に無効化するのを主たる防御とし、保存時刻（`NSUserDefaults`に併記）による閾値判定は「切断が一度も観測されなかった場合」の保険とする。閾値は`BGTaskScheduler`の実起動間隔に合わせるのではなく「これより古い情報は通知として無意味」というアプリ側の基準(15分)とし、OSの起動がそれより遅れた場合は検知を諦める意図的なトレードオフとした（2節を参照）。また `NWPathMonitor` 起動直後の未確定な連続発火に対応するため、`isBatchLaunch` 時は一定時間の静止(デバウンス)を待ってから確定値として扱う
  - [x] iOS 用 `BackgroundMonitoringServiceImpl`（iosMain）にて `BGTaskScheduler` を実装する。状態の保存/復元は `NetworkConnectivityImpl` に委譲。`NetworkNotificationPresenter` を自身で実装し、通知発火は同じく本タスクで実装する `NetworkNotifierImpl`（UNUserNotificationCenter 版、iosMain）に委譲する。`statusPresenter` は no-op、`register()` は interface 外公開、launchHandler 内での再投入と監視中フラグを含む（2 節末尾の設計確定事項を参照）。検証は `:shared` の iOS ターゲットコンパイル + iosTest まで（実機での実起動確認は次項）
  - [ ] Xcode プロジェクト・Swift 側（`AppContainer` 相当の DI、ViewModel/UI のネイティブ実装。ViewModel が両 Presenter を直接実装し、scenePhase による監視の cancel/再構成を含む）の追加。iosMain の `NotificationPermissionRepositoryImpl`（UNUserNotificationCenter 版）もここで実装する。`Info.plist` の `BGTaskSchedulerPermittedIdentifiers` 宣言と実機/シミュレータでの実起動確認を含む
  - [ ] 監視の開始に失敗したことをユーザーへ提示する。現在 `BackgroundMonitoringService.start()` は `Unit` を返すため、iOS では予約が拒否されても待機表示のまま理由が分からず、Android では `Loading` のまま抜けられない。共通 interface に失敗を伝える経路がないことが原因で、Android にも影響する（issue #22）
  - [ ] （上記2点の完了後・独立タスク）Android 側 `platform` 実装（`NetworkConnectivityImpl` / `NetworkNotifierImpl` / `ForegroundMonitoringService` 等）を `:app` から `:shared/androidMain` へ移動し、iOS(`iosMain`)と配置を揃える

> NOTE: 上記の Android `platform` 移動について。phase2 では `monitor`/`viewmodel`/`ui`/`platform` をまとめて `:app` に残置したが、その根拠（1.1節、`StateFlow` が Swift から直接購読できない問題）は `monitor`/`viewmodel` 固有のものであり、`platform`（gateway 実装）には本来当てはまらない。`platform` は Kotlin が Kotlin のインターフェースを実装するだけの話で、Android は `:app` と `:shared/androidMain` のどちらに置いても技術的制約は同じ（iOS は Swift が `commonMain` の Kotlin インターフェースを実装できないため `shared/iosMain` に置かざるを得なかった、という技術的必然があるのとは異なる）。したがって `platform` が `:app` に残っているのは「意図した設計」というより phase2 で他の3つと一緒くたに扱われた経緯によるもので、`:shared/androidMain` へ移してiOSと配置を揃える方が構造的には筋が良い。ただし `ForegroundMonitoringService` は `AndroidManifest.xml` に登録された実際の `Service` クラスであり、単純なファイル移動では済まずマニフェスト・`AppContainer` の DI 配線の見直しを伴う。この移動は iOS 側の残タスク（`BackgroundMonitoringServiceImpl`・Swift/UI）と技術的依存関係が無く、独立して後回しにできるため、既存Androidの動作確認とiOSの新規実装検証が混ざらないよう、上記2点を先に完了させてから着手する。
