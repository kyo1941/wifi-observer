package com.example.wifi_observer.domain.gateway

import com.example.wifi_observer.domain.model.NetworkMonitoringStatus

interface NetworkStatusPresenter {
    fun onNetworkStatusUpdated(status: NetworkMonitoringStatus)
}
