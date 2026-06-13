package com.example.wifi_observer

import com.example.wifi_observer.model.NetworkMonitoringStatus
import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.platform.interfaces.NetworkConnectivity
import com.example.wifi_observer.platform.interfaces.NetworkNotificationPresenter
import com.example.wifi_observer.viewmodel.NetworkStatusPresenter
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class NetworkUseCase(
    private val networkConnectivity: NetworkConnectivity,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    suspend fun observe(
        notificationPresenter: NetworkNotificationPresenter,
        statusPresenter: NetworkStatusPresenter,
    ) {
        var lastConnectedType: NetworkStatus.NetworkType? = null
        var disconnectedTime: TimeMark? = null
        networkConnectivity.observeNetworkStatus().collect { result ->
            result.fold(
                onSuccess = { current ->
                    when (current) {
                        is NetworkStatus.Connected -> {
                            // NOTE: ネットワーク切り替え時は Wifi -> NotConnected -> Mobile と観測されることがあるため、切断からの経過時間が grace period 内なら実質的な Wifi -> Mobile 切り替えとして扱う
                            val isShortInterruption =
                                disconnectedTime?.let { it.elapsedNow() <= WIFI_TO_MOBILE_GRACE } ?: true
                            if (lastConnectedType == NetworkStatus.NetworkType.Wifi &&
                                current.type == NetworkStatus.NetworkType.Mobile &&
                                isShortInterruption
                            ) {
                                notificationPresenter.displayNotification()
                            }
                            lastConnectedType = current.type
                            disconnectedTime = null
                        }
                        NetworkStatus.NotConnected -> {
                            if (disconnectedTime == null) {
                                disconnectedTime = timeSource.markNow()
                            }
                        }
                    }
                    statusPresenter.onNetworkStatusUpdated(NetworkMonitoringStatus.Available(current))
                },
                onFailure = { throwable ->
                    statusPresenter.onNetworkStatusUpdated(NetworkMonitoringStatus.Failed)
                },
            )
        }
    }

    companion object {
        private val WIFI_TO_MOBILE_GRACE = 5.seconds
    }
}
