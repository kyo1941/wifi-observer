package com.example.wifi_observer

import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.platform.NetworkNotifierImpl
import com.example.wifi_observer.platform.interfaces.BackgroundMonitoringService
import com.example.wifi_observer.platform.interfaces.NetworkNotificationPresenter
import com.example.wifi_observer.viewmodel.NetworkStatusPresenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(
    private val networkUseCase: NetworkUseCase,
    private val networkNotifier: NetworkNotifierImpl,
    private val backgroundMonitoringService: BackgroundMonitoringService,
) : NetworkNotificationPresenter,
    NetworkStatusPresenter {
    // FIXME: ResultはUI層に持ち込まずUseCase内でハンドリングする。また、Result の nullable も意図がちぐはぐなので修正する。
    private val _status = MutableStateFlow<Result<NetworkStatus>?>(null)
    val status: StateFlow<Result<NetworkStatus>?> = _status.asStateFlow()

    fun start() = backgroundMonitoringService.start()

    fun stop() {
        backgroundMonitoringService.stop()
        _status.value = null
    }

    suspend fun observe() {
        networkUseCase.observe(notificationPresenter = this, statusPresenter = this)
    }

    override fun displayNotification() {
        networkNotifier.notifyWifiToMobile()
    }

    override fun onNetworkStatusUpdated(status: Result<NetworkStatus>) {
        _status.value = status
    }
}
