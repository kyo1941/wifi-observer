package com.example.wifi_observer.platform.interfaces

import com.example.wifi_observer.model.NetworkStatus
import kotlinx.coroutines.flow.Flow

interface NetworkConnectivity {
    fun observeNetworkStatus(): Flow<Result<NetworkStatus>>
}