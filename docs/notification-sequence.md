# WiFi→モバイル通知 シーケンス図

## 通常フロー（アプリ起動〜通知発火）

FGS と ViewModel はそれぞれ独立して `NetworkUseCase.observe()` を呼ぶ。FGS は通知発火を担い、ViewModel は UI 更新を担う。

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "MainActivity /\nNetworkViewModel" as ViewModel #E8E8FF
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkUseCase" as UseCase #DDEEFF
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC
participant "NetworkNotifierImpl" as Notifier #AADDAA
participant "NotificationManager\n(Android OS)" as NotifOS #CCCCCC

ユーザー -> ViewModel: アプリ起動・監視開始ボタンをタップ
ViewModel -> Service: start() (via BackgroundMonitoringServiceController)
Service -> Service: startForeground()\n「監視中」常時通知を表示\n(IMPORTANCE_MIN)
Service -> UseCase: observe(serviceScope, notificationPresenter=this)
UseCase -> Connectivity: collect observeNetworkStatus()
Connectivity -> OS: registerDefaultNetworkCallback() [FGS用]

ViewModel -> UseCase: observe(viewModelScope, statusPresenter=this)
UseCase -> Connectivity: collect observeNetworkStatus()
Connectivity -> OS: registerDefaultNetworkCallback() [ViewModel用]

note over OS: WiFi 接続中

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_WIFI)
Connectivity --> UseCase: emit(NetworkStatus.Connected(Wifi)) × 2
UseCase -> UseCase: previousStatus = Wifi として記録（各観測で独立）
UseCase -> ViewModel: presentCurrentNetworkStatus(Wifi)

note over OS: WiFi → モバイル回線に切り替わる

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_CELLULAR)
Connectivity --> UseCase: emit(NetworkStatus.Connected(Mobile)) × 2

group FGS 側の観測（通知担当）
    UseCase -> UseCase: previousStatus=Wifi かつ current=Mobile を検知
    UseCase -> Service: displayNotification()
    Service -> Notifier: notifyWifiToMobile()
    Notifier -> NotifOS: notify()\n「モバイル回線に切り替わりました」
    NotifOS --> ユーザー: プッシュ通知
end

group ViewModel 側の観測（UI担当）
    UseCase -> ViewModel: presentCurrentNetworkStatus(Mobile)
    ViewModel -> ViewModel: _uiState 更新
end
@enduml
```

## タスクキル後のフロー

ViewModel は破棄されるが FGS は生存し続けるため、FGS 側の観測が通知を発火する。

```plantuml
@startuml
skinparam sequenceArrowThickness 2
skinparam roundcorner 5

actor ユーザー
participant "MainActivity /\nNetworkViewModel" as ViewModel #FFDDDD
participant "ForegroundMonitoringService" as Service #AADDAA
participant "NetworkUseCase" as UseCase #DDEEFF
participant "NetworkConnectivityImpl" as Connectivity #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC
participant "NetworkNotifierImpl" as Notifier #AADDAA
participant "NotificationManager\n(Android OS)" as NotifOS #CCCCCC

ユーザー -> ViewModel: アプリ起動・監視開始
ViewModel -> Service: start()
Service -> Service: startForeground()
Service -> UseCase: observe(serviceScope, notificationPresenter=this)
UseCase -> Connectivity: collect observeNetworkStatus()
Connectivity -> OS: registerDefaultNetworkCallback() [FGS用]

ViewModel -> UseCase: observe(viewModelScope, statusPresenter=this)
Connectivity -> OS: registerDefaultNetworkCallback() [ViewModel用]

ユーザー -> ViewModel: タスクキル
ViewModel ->x ViewModel: プロセス終了（Activity/ViewModel破棄）
note over OS: viewModelScope キャンセル → ViewModel 側の観測停止\nunregisterNetworkCallback() [ViewModel用]
note over Service: FGS は生存し続け、\nserviceScope の観測をアクティブに保つ

note over OS: WiFi → モバイル回線に切り替わる

OS --> Connectivity: onCapabilitiesChanged(TRANSPORT_CELLULAR)
Connectivity --> UseCase: emit(NetworkStatus.Connected(Mobile)) [FGS用のみ]

UseCase -> UseCase: previousStatus=Wifi かつ current=Mobile を検知
UseCase -> Service: displayNotification()
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
participant "NetworkViewModel" as ViewModel #E8E8FF
participant "ForegroundMonitoringServiceController" as Controller #AADDAA
participant "ForegroundMonitoringService" as Service #AADDAA
participant "ConnectivityManager\n(Android OS)" as OS #CCCCCC

ユーザー -> ViewModel: 監視停止ボタンをタップ
ViewModel -> Controller: stop()
Controller -> Service: stopService()
Service -> Service: onDestroy()\nserviceScope.cancel()
note over Service: serviceScope キャンセル → FGS 側の観測停止
Service -> OS: unregisterNetworkCallback() [FGS用]
Service -> Service: stopForeground()\n常時通知を消去

ViewModel -> ViewModel: observeJob.cancel()
note over ViewModel: viewModelScope のジョブキャンセル → ViewModel 側の観測停止
ViewModel -> OS: unregisterNetworkCallback() [ViewModel用]
ViewModel -> ViewModel: _uiState = Init
@enduml
```
