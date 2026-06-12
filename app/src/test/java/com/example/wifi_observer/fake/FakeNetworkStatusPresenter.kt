package com.example.wifi_observer.fake

import com.example.wifi_observer.model.NetworkMonitoringStatus
import com.example.wifi_observer.viewmodel.NetworkStatusPresenter

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
