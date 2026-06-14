package com.example.wifi_observer.fake

import com.example.wifi_observer.domain.gateway.NetworkStatusPresenter
import com.example.wifi_observer.domain.model.NetworkMonitoringStatus

/**
 * 受け取った状態更新を記録する Fake
 */
internal class FakeNetworkStatusPresenter : NetworkStatusPresenter {
    private val mutableStatuses = mutableListOf<NetworkMonitoringStatus>()
    val statuses: List<NetworkMonitoringStatus> get() = mutableStatuses

    override fun onNetworkStatusUpdated(status: NetworkMonitoringStatus) {
        mutableStatuses.add(status)
    }
}
