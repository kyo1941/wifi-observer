# バックグラウンドネットワーク監視 設計

## 概要

アプリをタスクキルした後もWiFi→モバイル回線の切り替えをリアルタイムに検知し、ユーザーへプッシュ通知を送る機能の設計。

## 技術選定

### なぜ Foreground Service か

Android 7以降、バックグラウンドアプリへの `CONNECTIVITY_CHANGE` ブロードキャストは無効化されている。
タスクキル後もリアルタイムに監視し続けるには Foreground Service 内で `ConnectivityManager.registerDefaultNetworkCallback` を保持し続けるしかない。

| 手段 | タスクキル後の生存 | リアルタイム性 | 通知の要否 |
|------|:-----------------:|:--------------:|:----------:|
| Foreground Service | ○ | ○ | 必須（最小化可能） |
| WorkManager | △（OS任せ） | × (最短15分) | 不要 |
| AlarmManager | △（Doze制限あり） | × | 不要 |
| BroadcastReceiver | × | ○ | 不要 |

## KMP 対応方針

将来的に iOS への対応を見据え、プラットフォーム固有の実装をインターフェースで抽象化する。

| 層 | 内容 | KMP モジュール |
|----|------|---------------|
| ビジネスロジック | `NetworkUseCase` | `commonMain` |
| 監視インターフェース | `NetworkConnectivity` | `commonMain` |
| 通知Presenterインターフェース | `NetworkNotificationPresenter` | `commonMain` |
| UI更新Presenterインターフェース | `NetworkStatusPresenter` | `commonMain` |
| バックグラウンド実行インターフェース | `BackgroundMonitoringService` | `commonMain` |
| Android 実装 | `NetworkConnectivityImpl`, `NetworkNotifierImpl`, `ForegroundMonitoringService`, `ForegroundMonitoringServiceController` | `androidMain` |
| iOS 実装 | `NetworkConnectivityImpl`, `NetworkNotifierImpl`, `BackgroundMonitoringServiceImpl` | `iosMain` |

### Presenter パターンによる責務分離

通知とUI更新の責務を2つのPresenterインターフェースに分離することで、それぞれのライフサイクルに合った実装者が担当できる。

| インターフェース | 実装者 | ライフサイクル |
|---|---|---|
| `NetworkNotificationPresenter` | `ForegroundMonitoringService` | FGS と同じ（タスクキル後も生存） |
| `NetworkStatusPresenter` | `NetworkViewModel` | UI と同じ（タスクキル後は破棄） |

`NetworkUseCase` は `observe(scope, notificationPresenter?, statusPresenter?)` を提供し、コルーチンスコープ内で監視ループを駆動する。Presenter は呼び出し元が注入するため、UseCase はプラットフォーム固有の実装に依存しない。

- **FGS**: `serviceScope` と自身（`NetworkNotificationPresenter`）を渡して `observe()` を呼ぶ。タスクキル後も通知を発火できる。
- **ViewModel**: `viewModelScope` と自身（`NetworkStatusPresenter`）を渡して `observe()` を呼ぶ。UI を更新する。

`ForegroundMonitoringServiceController` は `Context` を使って FGS を起動・停止する薄いラッパーであり、`BackgroundMonitoringService` インターフェースを実装する。

## クラス図

```plantuml
@startuml
skinparam packageStyle rectangle
skinparam linetype ortho

package "commonMain" #DDEEFF {
    interface NetworkConnectivity {
        +observeNetworkStatus(): Flow<Result<NetworkStatus>>
    }

    interface NetworkNotificationPresenter {
        +displayNotification()
    }

    interface NetworkStatusPresenter {
        +presentCurrentNetworkStatus(status: NetworkUiStatus)
    }

    interface BackgroundMonitoringService {
        +start()
        +stop()
    }

    class NetworkUseCase {
        -networkConnectivity: NetworkConnectivity
        +observe(scope, notificationPresenter?, statusPresenter?): Job
        +getCurrentNetworkStatus(): NetworkUiStatus
    }

    class NetworkStatus <<sealed>> {
        +Connected(type: NetworkType)
        +NotConnected
    }

    NetworkUseCase --> NetworkConnectivity
    NetworkUseCase ..> NetworkNotificationPresenter
    NetworkUseCase ..> NetworkStatusPresenter
}

package "presentation" #E8E8FF {
    class NetworkViewModel {
        -networkUseCase: NetworkUseCase
        -backgroundMonitoringService: BackgroundMonitoringService
        +observeNetworkStatus()
        +stopObserveNetworkStatus()
        +presentCurrentNetworkStatus(status)
    }

    NetworkViewModel ..|> NetworkStatusPresenter
    NetworkViewModel --> NetworkUseCase
    NetworkViewModel --> BackgroundMonitoringService
}

package "androidMain" #DDFFDD {
    class NetworkConnectivityImpl {
        -connectivityManager: ConnectivityManager
        +observeNetworkStatus(): Flow<Result<NetworkStatus>>
    }

    class NetworkNotifierImpl {
        -context: Context
        +notifyWifiToMobile()
    }

    class ForegroundMonitoringService {
        -networkUseCase: NetworkUseCase
        -networkNotifier: NetworkNotifierImpl
        +onStartCommand(): Int
        +displayNotification()
    }

    class ForegroundMonitoringServiceController {
        -context: Context
        +start()
        +stop()
    }

    NetworkConnectivityImpl ..|> NetworkConnectivity
    ForegroundMonitoringService ..|> NetworkNotificationPresenter
    ForegroundMonitoringService --> NetworkUseCase
    ForegroundMonitoringService --> NetworkNotifierImpl
    ForegroundMonitoringServiceController ..|> BackgroundMonitoringService
}

package "iosMain" #FFF3DD {
    class "NetworkConnectivityImpl" as iOSConnectivity {
        -pathMonitor: NWPathMonitor
        +observeNetworkStatus(): Flow<Result<NetworkStatus>>
    }

    class "NetworkNotifierImpl" as iOSNotifier {
        +notifyWifiToMobile()
    }

    class BackgroundMonitoringServiceImpl {
        -networkUseCase: NetworkUseCase
        +start()
        +stop()
        +displayNotification()
    }

    iOSConnectivity ..|> NetworkConnectivity
    BackgroundMonitoringServiceImpl ..|> BackgroundMonitoringService
    BackgroundMonitoringServiceImpl ..|> NetworkNotificationPresenter
    BackgroundMonitoringServiceImpl --> NetworkUseCase
    BackgroundMonitoringServiceImpl --> iOSNotifier
}
@enduml
```

## 実装メモ

### Android 13/14 (Target SDK 36) の制約と対応

1. **通知パーミッション（Android 13+ / API 33+）**
   - `POST_NOTIFICATIONS` 権限の許可はバックグラウンドでの通知表示に必要ですが、Foreground Service (FGS) 自体の起動・動作自体は通知権限がなくても実行可能です（その場合、通知は表示されません）。ただし、ユーザーにサービスの稼働を示すために、アプリ起動時または監視機能の有効化時に権限要求ダイアログを表示することが推奨されます。

2. **Foreground Service Type の指定（Android 14+ / API 34+）**
   - Foreground Service の起動にあたり、マニフェストおよびコード内での `foregroundServiceType` の明示的な指定が必須。
   - 今回の「ネットワーク監視」は定義済みのどのカテゴリにも直接合致しないため、`specialUse` を使用する。
   - **AndroidManifest.xml への追加:**
     ```xml
     <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
     <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

     <service
         android:name=".platform.ForegroundMonitoringService"
         android:foregroundServiceType="specialUse"
         android:exported="false">
         <property
             android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
             android:value="Network switching observer for notification" />
     </service>
     ```
   - **Service 起動時の指定（コード内）:**
     ```kotlin
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
         startForeground(
             NOTIFICATION_ID,
             notification,
             ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
         )
     } else {
         startForeground(NOTIFICATION_ID, notification)
     }
     ```

### Foreground Service の常時通知について

Android 8以降、Foreground Service には通知チャンネルが必須。
ユーザー体験を損なわないよう、常時通知は最小化設定を推奨する。

```kotlin
NotificationChannel(
    CHANNEL_ID_MONITORING,
    "ネットワーク監視",
    NotificationManager.IMPORTANCE_MIN  // 通知バーの最下部に表示、音なし
)
```

WiFi→モバイル検知時の通知は別チャンネルで `IMPORTANCE_HIGH` に設定する。

### 状態遷移の検知ロジック

`previousStatus` は `observe()` 呼び出しごとのコルーチンローカル変数として保持する。UseCase インスタンスに状態を持たせないことで、複数の呼び出し元（FGS・ViewModel）がそれぞれ独立した状態を持つ。

```kotlin
fun observe(
    scope: CoroutineScope,
    notificationPresenter: NetworkNotificationPresenter? = null,
    statusPresenter: NetworkStatusPresenter? = null,
): Job = scope.launch {
    var previousStatus: NetworkStatus? = null
    networkConnectivity.observeNetworkStatus().collect { result ->
        val current = result.getOrNull()
        if (current != null) {
            val previous = previousStatus
            if (previous is NetworkStatus.Connected &&
                previous.type == NetworkStatus.NetworkType.Wifi &&
                current is NetworkStatus.Connected &&
                current.type == NetworkStatus.NetworkType.Mobile
            ) {
                notificationPresenter?.displayNotification()
            }
            previousStatus = current
        }
        statusPresenter?.presentCurrentNetworkStatus(result.toUiStatus())
    }
}
```

### iOS 側のバックグラウンド動作への対応（将来）

iOS ではセキュリティと省電力の制限により、バックグラウンドでのリアルタイム監視（常時ソケット接続や常時ポーリングなど）は不可能です。
そのため、`BackgroundMonitoringServiceImpl` の iOS 側の実装では以下のハイブリッドアプローチを想定します。

1. **フォアグラウンド時**: `NWPathMonitor` を利用してリアルタイムにネットワーク変更を監視。
2. **バックグラウンド時**: `BGTaskScheduler`（Background Tasks）を用いて、OSが許可したバックグラウンド実行タイミング（最短で十数分〜数時間間隔）で現在のネットワーク状態を取得。
3. **状態永続化**: アプリのプロセスがバックグラウンドタスク実行時に都度新しく起動するため、前回のネットワーク状態を `NSUserDefaults`（KMP では `Settings` ライブラリ等）に保存し、バックグラウンドタスク起動時に前回の値と比較して WiFi→モバイル の遷移を検知・ローカル通知を発火する。
