package com.example.wifi_observer

import com.example.wifi_observer.model.NetworkMonitoringStatus
import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.platform.interfaces.NetworkConnectivity
import com.example.wifi_observer.platform.interfaces.NetworkNotificationPresenter
import com.example.wifi_observer.viewmodel.NetworkStatusPresenter

class NetworkUseCase(
    private val networkConnectivity: NetworkConnectivity,
) {
    suspend fun observe(
        notificationPresenter: NetworkNotificationPresenter,
        statusPresenter: NetworkStatusPresenter,
    ) {
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
                    notificationPresenter.displayNotification()
                }
                previousStatus = current
            }
            statusPresenter.onNetworkStatusUpdated(result.toMonitoringStatus())
        }
    }

    private fun Result<NetworkStatus>.toMonitoringStatus(): NetworkMonitoringStatus =
        fold(
            onSuccess = { status ->
                NetworkMonitoringStatus.Available(status)
            },
            onFailure = {
                NetworkMonitoringStatus.Failed
            },
        )
}
