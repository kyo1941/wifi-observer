# KMP 移行ロードマップ & プラットフォーム設計ガイド

本ドキュメントは、現在 Android ファーストで作成しているネットワーク監視機能を将来的に Kotlin Multiplatform (KMP) に移行する際の、モジュール構造、パッケージ設計、およびプラットフォーム固有機能の抽象化指針をまとめた仕様書です。

---

## 1. モジュール構成とファイル配置予定図

KMP 移行時には、現在の Android アプリケーション構造から以下のようにモジュールを分割し、共通ロジック (`commonMain`) とプラットフォーム具象コード (`androidMain` / `iosMain`) を分類します。

```text
shared/
├── src/
│   ├── commonMain/kotlin/com/example/wifi_observer/
│   │   ├── model/
│   │   │   └── NetworkStatus.kt (WiFi/Mobile定義)
│   │   ├── platform/interfaces/
│   │   │   ├── NetworkConnectivity.kt (接続状態の観測I/F)
│   │   │   ├── NetworkNotifier.kt (通知送信I/F)
│   │   │   └── BackgroundMonitoringService.kt (監視開始・停止I/F)
│   │   ├── viewmodel/
│   │   │   └── NetworkViewModel.kt (薄い制御ViewModel)
│   │   └── NetworkUseCase.kt (★状態遷移の検知・通知発火のコアビジネスロジック)
│   │
│   ├── androidMain/kotlin/com/example/wifi_observer/
│   │   └── platform/
│   │       ├── NetworkConnectivityImpl.kt (ConnectivityManager利用)
│   │       ├── NetworkNotifierImpl.kt (NotificationManager利用)
│   │       └── ForegroundMonitoringService.kt (薄いForeground Service wrapper)
│   │
│   └── iosMain/kotlin/com/example/wifi_observer/
│       └── platform/
│           ├── NetworkConnectivityImpl.kt (NWPathMonitor利用)
│           ├── NetworkNotifierImpl.kt (UNUserNotificationCenter利用)
│           └── BackgroundMonitoringServiceImpl.kt (薄いBGTaskScheduler wrapper)
```

---

## 2. 状態の永続化とプラットフォーム間の差異の埋め方

iOS では、バックグラウンドでのリアルタイム監視が制限されているため、`BGTaskScheduler` による定期評価（バッチ処理）の際に**「前回の状態」**を保持しておく必要があります。しかし、アプリのプロセスはタスク起動の都度新しく立ち上がるため、メモリ上の変数（`previousStatus`）では状態を維持できません。

この制約を解消するため、将来的な KMP 移行時には以下の永続化機構を組み込みます。

### 永続化ライブラリの利用設計
KMP 共通モジュールでキーバリュー型永続化を扱うために、**[multiplatform-settings](https://github.com/russhwolf/multiplatform-settings)** を導入します。

- **Android 側**: `SharedPreferences` にマッピング
- **iOS 側**: `NSUserDefaults` にマッピング

### 永続化を用いた状態検知フロー（共通ロジック化）

`NetworkUseCase` は、メモリ上の `previousStatus` 変数のフォールバックとして、プラットフォームごとに注入される Key-Value 永続化ストア（または `Settings` インターフェース）を参照し、バックグラウンド起動時にも正しく WiFi → モバイル の状態遷移を検知できるように設計します。

#### 状態チェックの疑似コード (共通ロジック):
```kotlin
class NetworkUseCase(
    private val networkConnectivity: NetworkConnectivity,
    private val networkNotifier: NetworkNotifier,
    private val settings: Settings // KMP 共通のキーバリュー永続化
) {
    companion object {
        private const val KEY_LAST_KNOWN_STATUS = "last_known_network_status"
    }

    fun observeNetworkStatus(): Flow<NetworkUiStatus> {
        return networkConnectivity.observeNetworkStatus()
            .onEach { result ->
                val current = result.getOrNull() ?: return@onEach
                val previous = getLastKnownStatus()

                // WiFi → Mobile への切り替え検知
                if (previous is NetworkStatus.Connected && previous.type == NetworkStatus.NetworkType.Wifi &&
                    current is NetworkStatus.Connected && current.type == NetworkStatus.NetworkType.Mobile) {
                    
                    networkNotifier.notifyWifiToMobile()
                }

                // 永続化ストアに現在の接続情報を保存
                saveStatus(current)
            }
            .map { it.toUiStatus() }
    }

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
        appContainer = AppContainer(
            context = this,
            networkConnectivity = NetworkConnectivityImpl(getSystemService(ConnectivityManager::class.java)),
            networkNotifier = NetworkNotifierImpl(this)
        )
    }
}
```

### iOS 側での初期化例 (Swift)
```swift
@main
struct WifiObserverApp: App {
    let appContainer: AppContainer

    init() {
        // iOS 固有の監視実装と通知実装を渡して KMP の AppContainer を初期化
        self.appContainer = AppContainer(
            networkConnectivity: iOSNetworkConnectivityImpl(),
            networkNotifier: iOSNetworkNotifierImpl()
        )
        
        // バックグラウンドタスクの初期登録
        appContainer.backgroundMonitoringService.start()
    }
}
```

---

## 4. KMP 移行フェーズチェックリスト

実装・移行を安全に進めるために、本仕様に基づいた段階的アプローチを定義します。

- [ ] **フェーズ 1: Android 側でのリアクティブ設計の完了（現在地）**
  - [x] 設計書をリアクティブストリーム（Flow内での判定＋薄いラッパー）に基づき更新
  - [ ] 共通インターフェース (`NetworkNotifier`, `BackgroundMonitoringService`) の Android 定義
  - [ ] 薄い `ForegroundMonitoringService` の実装と `POST_NOTIFICATIONS` 権限対応の完了
- [ ] **フェーズ 2: 共有モジュール (shared) の新設とコード抽出**
  - [ ] `shared` マルチプラットフォームモジュールを Gradle に作成
  - [ ] `NetworkStatus`, `NetworkConnectivity`, `NetworkNotifier`, `BackgroundMonitoringService` を `commonMain` に移動
  - [ ] `NetworkUseCase` および `NetworkViewModel` を `commonMain` に移動
- [ ] **フェーズ 3: 状態永続化の共通化**
  - [ ] `multiplatform-settings` の依存追加
  - [ ] `NetworkUseCase` の前回の状態比較処理を、メモリ上の変数から `Settings` ストアを参照する形に拡張
- [ ] **フェーズ 4: iOS プラットフォーム実装の追加**
  - [ ] iOS `iosMain` において `NWPathMonitor` と `UNUserNotificationCenter` を用いた具象クラスを実装
  - [ ] iOS 用 `BackgroundMonitoringServiceImpl` にて `BGTaskScheduler` と `UserDefaults` の統合を実装
