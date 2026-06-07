# WiFi→モバイル通知 シーケンス図

## 通常フロー（アプリ起動〜通知発火）

`NetworkViewModel` は `NetworkUseCase` を直接 observe しない。UI は `NetworkMonitor.status` を購読し、監視開始・停止だけを `NetworkMonitor` に依頼する。

FGS は `serviceScope` 上で `NetworkMonitor.observe()` を起動する。`NetworkMonitor` は `NetworkNotificationPresenter` / `NetworkStatusPresenter` として `NetworkUseCase` に渡され、UseCase からの通知要求と状態更新を受け取る。

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "NetworkViewModel" as ViewModel #E8E8FF
participant "NotificationPermissionUseCase" as PermissionUseCase #DDEEFF
participant "NotificationPermissionRepositoryImpl" as PermissionRepo #AADDAA
participant "NetworkMonitor" as Monitor #DDEEFF
participant "ForegroundMonitoringServiceController" as Controller #AADDAA
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkUseCase" as UseCase #DDEEFF
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC
participant "NetworkNotifierImpl" as Notifier #AADDAA
participant "NotificationManager\n(Android OS)" as NotifOS #CCCCCC

ユーザー -> ViewModel: 監視開始ボタンをタップ
ViewModel -> PermissionUseCase: isMonitoringStartable(presenter=this)
PermissionUseCase -> PermissionRepo: getStatus()

alt Requestable
    PermissionUseCase -> ViewModel: requestNotificationPermission()
    ViewModel --> ユーザー: 通知権限ダイアログを表示
    ユーザー --> ViewModel: 許可
    ViewModel -> PermissionUseCase: updateNotificationPermission(result=Granted,\npresenter=this)
    PermissionUseCase -> PermissionRepo: recordPermissionDecision()
    PermissionUseCase -> PermissionRepo: getStatus()
else RequiredButNotGranted または拒否
    PermissionUseCase -> ViewModel: showNotificationPermissionRequired()
    ViewModel --> ユーザー: Snackbar\n通知許可が必要
    note over ViewModel: 監視は開始しない
else Granted / NotRequired
end

note over ViewModel, Monitor: 権限が許可済み、または権限不要の場合
ViewModel -> Monitor: start()
Monitor -> Controller: start()
Controller -> Service: startForegroundService()\n(API 25以下は startService())
Service -> Service: startForeground()\n「監視中」常時通知を表示
Service -> Service: observeJob が active でないことを確認
Service -> Monitor: serviceScope.launch { observe() }
Monitor -> UseCase: observe(notificationPresenter=this,\nstatusPresenter=this)
UseCase -> Connectivity: collect observeNetworkStatus()
Connectivity -> OS: registerDefaultNetworkCallback()

ViewModel -> Monitor: status を購読

note over OS: WiFi 接続中

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_WIFI)
Connectivity --> UseCase: emit(NetworkStatus.Connected(Wifi))
UseCase -> UseCase: previousStatus = Wifi
UseCase -> Monitor: onNetworkStatusUpdated(Wifi)
Monitor --> ViewModel: status StateFlow 更新

note over OS: WiFi → モバイル回線に切り替わる

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_CELLULAR)
Connectivity --> UseCase: emit(NetworkStatus.Connected(Mobile))
UseCase -> UseCase: previousStatus=Wifi かつ current=Mobile を検知
UseCase -> Monitor: displayNotification()
Monitor -> Notifier: notifyWifiToMobile()
Notifier -> NotifOS: notify()\n「モバイル回線に切り替わりました」
NotifOS --> ユーザー: プッシュ通知
UseCase -> Monitor: onNetworkStatusUpdated(Mobile)
Monitor --> ViewModel: status StateFlow 更新
@enduml
```

## 多重起動防止フロー

`startForegroundService()` はサービス起動済みでも `onStartCommand()` を再度呼ぶため、FGS 側で監視 coroutine の `Job` を保持して多重登録を防ぐ。

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

participant "NetworkViewModel" as ViewModel #E8E8FF
participant "NetworkMonitor" as Monitor #DDEEFF
participant "ForegroundMonitoringService" as Service #AADDAA

ViewModel -> Monitor: start()
Monitor -> Service: startForegroundService()
Service -> Service: observeJob == null
Service -> Monitor: serviceScope.launch { observe() }

ViewModel -> Monitor: start() 再実行
Monitor -> Service: startForegroundService()
Service -> Service: observeJob?.isActive == true
note over Service: 既存の監視を使い、新しい NetworkCallback は登録しない
@enduml
```

## タスクキル後のフロー

ViewModel は破棄されるが、FGS とその `serviceScope` が生存している限り監視は継続する。UseCase の出力先は `NetworkMonitor` なので、UI が存在しない状態でも通知は発火できる。

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "NetworkViewModel" as ViewModel #FFDDDD
participant "NetworkMonitor" as Monitor #DDEEFF
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkUseCase" as UseCase #DDEEFF
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC
participant "NetworkNotifierImpl" as Notifier #AADDAA
participant "NotificationManager\n(Android OS)" as NotifOS #CCCCCC

ユーザー -> ViewModel: 監視開始
ViewModel -> Monitor: start()
Monitor -> Service: startForegroundService()
Service -> Monitor: serviceScope.launch { observe() }
Monitor -> UseCase: observe(notificationPresenter=this,\nstatusPresenter=this)
UseCase -> Connectivity: collect observeNetworkStatus()
Connectivity -> OS: registerDefaultNetworkCallback()

ユーザー -> ViewModel: タスクキル
ViewModel ->x ViewModel: Activity/ViewModel 破棄
note over Service: FGS は生存し、serviceScope の監視を継続

note over OS: WiFi → モバイル回線に切り替わる

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_CELLULAR)
Connectivity --> UseCase: emit(NetworkStatus.Connected(Mobile))
UseCase -> UseCase: previousStatus=Wifi かつ current=Mobile を検知
UseCase -> Monitor: displayNotification()
Monitor -> Notifier: notifyWifiToMobile()
Notifier -> NotifOS: notify()
NotifOS --> ユーザー: プッシュ通知（アプリ画面は不要）
@enduml
```

## 停止フロー

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "NetworkViewModel" as ViewModel #E8E8FF
participant "NetworkMonitor" as Monitor #DDEEFF
participant "ForegroundMonitoringServiceController" as Controller #AADDAA
participant "ForegroundMonitoringService" as Service #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC

ユーザー -> ViewModel: 監視停止ボタンをタップ
ViewModel -> Monitor: stop()
Monitor -> Controller: stop()
Controller -> Service: stopService()
Monitor -> Monitor: status = null
Service -> Service: onDestroy()\nserviceScope.cancel()
note over Service: serviceScope キャンセル → observeJob と FGS 側の観測停止
Service -> OS: unregisterNetworkCallback()
Service -> Service: stopForeground()\n常時通知を消去
Monitor --> ViewModel: status StateFlow 更新
@enduml
```
