package com.example.wifi_observer.model

sealed interface NotificationPermissionRequestResult {
    data object Granted : NotificationPermissionRequestResult

    data object Denied : NotificationPermissionRequestResult

    data object Dismissed : NotificationPermissionRequestResult
}
