package com.example.wifi_observer.viewmodel

import com.example.wifi_observer.model.NetworkMonitoringStatus

interface NetworkStatusPresenter {
    fun onNetworkStatusUpdated(status: NetworkMonitoringStatus)
}
