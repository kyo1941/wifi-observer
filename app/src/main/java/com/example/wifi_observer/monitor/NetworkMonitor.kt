package com.example.wifi_observer.monitor

import com.example.wifi_observer.domain.gateway.BackgroundMonitoringService
import com.example.wifi_observer.domain.gateway.NetworkNotificationPresenter
import com.example.wifi_observer.domain.gateway.NetworkNotifier
import com.example.wifi_observer.domain.gateway.NetworkStatusPresenter
import com.example.wifi_observer.domain.model.NetworkMonitoringStatus
import com.example.wifi_observer.domain.usecase.NetworkUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(
    private val networkUseCase: NetworkUseCase,
    private val networkNotifier: NetworkNotifier,
    private val backgroundMonitoringService: BackgroundMonitoringService,
) : NetworkNotificationPresenter,
    NetworkStatusPresenter {
    private val _status = MutableStateFlow<NetworkMonitoringStatus?>(null)
    val status: StateFlow<NetworkMonitoringStatus?> = _status.asStateFlow()

    @Volatile
    private var observeJob: Job? = null

    fun start() {
        backgroundMonitoringService.start()
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
        backgroundMonitoringService.stop()
        _status.value = null
    }

    suspend fun observe() {
        observeJob = currentCoroutineContext()[Job]
        networkUseCase.observe(notificationPresenter = this, statusPresenter = this)
    }

    override fun displayNotification() {
        networkNotifier.notifyWifiToMobile()
    }

    override fun onNetworkStatusUpdated(status: NetworkMonitoringStatus) {
        _status.value = status
    }
}
