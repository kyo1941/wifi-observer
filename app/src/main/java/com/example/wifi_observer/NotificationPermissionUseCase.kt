package com.example.wifi_observer

import com.example.wifi_observer.model.NotificationPermissionStatus
import com.example.wifi_observer.platform.interfaces.NotificationPermissionRepository
import com.example.wifi_observer.viewmodel.NotificationPermissionPresenter

class NotificationPermissionUseCase(
    private val notificationPermissionRepository: NotificationPermissionRepository,
) {
    suspend fun isMonitoringStartable(
        presenter: NotificationPermissionPresenter,
    ): Boolean =
        when (notificationPermissionRepository.getStatus()) {
            is NotificationPermissionStatus.NotRequired,
            is NotificationPermissionStatus.Granted -> true

            is NotificationPermissionStatus.Requestable -> {
                presenter.requestNotificationPermission()
                false
            }

            is NotificationPermissionStatus.RequiredButNotGranted -> {
                presenter.showNotificationPermissionRequired()
                false
            }
        }

    suspend fun updateNotificationPermission(
        isGranted: Boolean,
        presenter: NotificationPermissionPresenter,
    ): Boolean {
        notificationPermissionRepository.recordRequested()

        if (!isGranted) {
            presenter.showNotificationPermissionRequired()
            return false
        }

        return when (notificationPermissionRepository.getStatus()) {
            is NotificationPermissionStatus.NotRequired,
            is NotificationPermissionStatus.Granted -> true

            is NotificationPermissionStatus.Requestable,
            is NotificationPermissionStatus.RequiredButNotGranted -> {
                presenter.showNotificationPermissionRequired()
                false
            }
        }
    }
}
