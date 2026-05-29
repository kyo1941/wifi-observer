package com.example.wifi_observer.viewmodel

sealed interface NetworkUiStatus {
    data object Loading : NetworkUiStatus

    data object Wifi : NetworkUiStatus

    data object Mobile : NetworkUiStatus

    data object Other : NetworkUiStatus

    data object NotConnected : NetworkUiStatus

    data class Error(
        val message: String,
    ) : NetworkUiStatus
}
