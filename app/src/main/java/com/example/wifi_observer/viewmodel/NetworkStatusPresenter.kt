package com.example.wifi_observer.viewmodel

import com.example.wifi_observer.model.NetworkStatus

interface NetworkStatusPresenter {
    fun onNetworkStatusUpdated(status: Result<NetworkStatus>)
}
