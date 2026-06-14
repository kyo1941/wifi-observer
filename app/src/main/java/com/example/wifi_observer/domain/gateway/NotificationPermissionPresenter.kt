package com.example.wifi_observer.domain.gateway

interface NotificationPermissionPresenter {
    fun requestNotificationPermission()

    fun showNotificationPermissionRequired()
}
