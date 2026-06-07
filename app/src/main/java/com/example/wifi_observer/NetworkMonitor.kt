package com.example.wifi_observer

import com.example.wifi_observer.model.NetworkMonitoringStatus
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
    private val _status = MutableStateFlow<NetworkMonitoringStatus?>(null)
    val status: StateFlow<NetworkMonitoringStatus?> = _status.asStateFlow()

    @Volatile
    private var isMonitoring = false

    fun start() {
        backgroundMonitoringService.start()
        isMonitoring = true
    }

    fun stop() {
        backgroundMonitoringService.stop()
        isMonitoring = false
        _status.value = null
    }

    suspend fun observe() {
        networkUseCase.observe(notificationPresenter = this, statusPresenter = this)
    }

    override fun displayNotification() {
        if (isMonitoring) {
            networkNotifier.notifyWifiToMobile()
        }
    }

    override fun onNetworkStatusUpdated(status: NetworkMonitoringStatus) {
        if (isMonitoring) {
            _status.value = status
        }
    }
}
