package com.example.wifi_observer.platform.interfaces

import com.example.wifi_observer.model.NotificationPermissionStatus

interface NotificationPermissionRepository {
    suspend fun getStatus(): NotificationPermissionStatus

    suspend fun recordPermissionDecision()
}
