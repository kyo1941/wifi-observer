# WiFi→モバイル通知 シーケンス図

## 通常フロー（アプリ起動〜通知発火）

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "MainActivity" as Activity
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC
participant "NetworkNotifierImpl" as Notifier #AADDAA
participant "NotificationManager\n(Android OS)" as NotifOS #CCCCCC

ユーザー -> Activity: アプリ起動
Activity -> Service: start()
Service -> Service: startForeground()\n「監視中」常時通知を表示\n(IMPORTANCE_MIN)
Service -> Connectivity: observeNetworkStatus().collect()
Connectivity -> OS: registerDefaultNetworkCallback()

note over OS: WiFi 接続中

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_WIFI)
Connectivity --> Service: emit(NetworkStatus.Connected(Wifi))
Service -> Service: previousStatus = Wifi として記録

note over OS: WiFi → モバイル回線に切り替わる

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_CELLULAR)
Connectivity --> Service: emit(NetworkStatus.Connected(Mobile))

Service -> Service: previousStatus=Wifi かつ\ncurrent=Mobile を検知
Service -> Notifier: notifyWifiToMobile()
Notifier -> NotifOS: notify()\n「モバイル回線に切り替わりました」
NotifOS --> ユーザー: プッシュ通知
@enduml
```

## タスクキル後のフロー

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "MainActivity" as Activity #FFDDDD
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC
participant "NetworkNotifierImpl" as Notifier #AADDAA
participant "NotificationManager\n(Android OS)" as NotifOS #CCCCCC

ユーザー -> Activity: アプリ起動
Activity -> Service: start()
Service -> Service: startForeground()
Service -> Connectivity: observeNetworkStatus().collect()
Connectivity -> OS: registerDefaultNetworkCallback()

ユーザー -> Activity: タスクキル
Activity ->x Activity: プロセス終了（Activity/ViewModel破棄）
note over Service: Foreground Service は生存し続ける

note over OS: WiFi → モバイル回線に切り替わる

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_CELLULAR)
Connectivity --> Service: emit(NetworkStatus.Connected(Mobile))
Service -> Notifier: notifyWifiToMobile()
Notifier -> NotifOS: notify()
NotifOS --> ユーザー: プッシュ通知（アプリ起動不要）
@enduml
```

## 停止フロー

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "MainActivity" as Activity
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC

ユーザー -> Activity: 監視停止ボタンをタップ
Activity -> Service: stop()
Service -> Connectivity: Flow のキャンセル
Connectivity -> OS: unregisterNetworkCallback()
Service -> Service: stopForeground()\n常時通知を消去
Service -> Service: stopSelf()
@enduml
```
