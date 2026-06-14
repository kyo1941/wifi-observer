package com.example.wifi_observer.domain.model

sealed interface NetworkMonitoringStatus {
    data class Available(
        val status: NetworkStatus,
    ) : NetworkMonitoringStatus

    data object Failed : NetworkMonitoringStatus
}
