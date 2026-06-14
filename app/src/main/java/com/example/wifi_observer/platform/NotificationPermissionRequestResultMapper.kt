package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.model.NotificationPermissionRequestResult

fun notificationPermissionRequestResult(
    isGranted: Boolean,
    shouldShowRationale: Boolean,
): NotificationPermissionRequestResult =
    when {
        isGranted -> NotificationPermissionRequestResult.Granted
        shouldShowRationale -> NotificationPermissionRequestResult.Denied
        else -> NotificationPermissionRequestResult.Dismissed
    }
