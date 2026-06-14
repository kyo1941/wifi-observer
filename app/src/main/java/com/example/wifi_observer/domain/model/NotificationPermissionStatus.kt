package com.example.wifi_observer.domain.model

sealed interface NotificationPermissionStatus {
    data object NotRequired : NotificationPermissionStatus

    data object Granted : NotificationPermissionStatus

    data object Requestable : NotificationPermissionStatus

    data object RequiredButNotGranted : NotificationPermissionStatus
}
