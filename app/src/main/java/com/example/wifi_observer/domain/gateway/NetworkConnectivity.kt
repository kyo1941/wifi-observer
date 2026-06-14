package com.example.wifi_observer.domain.gateway

import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.coroutines.flow.Flow

interface NetworkConnectivity {
    fun observeNetworkStatus(): Flow<Result<NetworkStatus>>
}
