package com.example.wifi_observer.domain.gateway

import com.example.wifi_observer.domain.model.NotificationPermissionStatus

interface NotificationPermissionRepository {
    suspend fun getStatus(): NotificationPermissionStatus

    suspend fun recordPermissionDecision()
}
