package com.example.wifi_observer.domain.usecase

import com.example.wifi_observer.domain.gateway.NotificationPermissionPresenter
import com.example.wifi_observer.domain.gateway.NotificationPermissionRepository
import com.example.wifi_observer.domain.model.NotificationPermissionRequestResult
import com.example.wifi_observer.domain.model.NotificationPermissionStatus

class NotificationPermissionUseCase(
    private val notificationPermissionRepository: NotificationPermissionRepository,
) {
    suspend fun isMonitoringStartable(presenter: NotificationPermissionPresenter): Boolean =
        when (notificationPermissionRepository.getStatus()) {
            is NotificationPermissionStatus.NotRequired,
            is NotificationPermissionStatus.Granted,
            -> true

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
        result: NotificationPermissionRequestResult,
        presenter: NotificationPermissionPresenter,
    ): Boolean {
        when (result) {
            is NotificationPermissionRequestResult.Dismissed -> {
                presenter.showNotificationPermissionRequired()
                return false
            }

            is NotificationPermissionRequestResult.Denied -> {
                notificationPermissionRepository.recordPermissionDecision()
                presenter.showNotificationPermissionRequired()
                return false
            }

            is NotificationPermissionRequestResult.Granted -> {
                notificationPermissionRepository.recordPermissionDecision()
            }
        }

        return when (notificationPermissionRepository.getStatus()) {
            is NotificationPermissionStatus.NotRequired,
            is NotificationPermissionStatus.Granted,
            -> true

            is NotificationPermissionStatus.Requestable,
            is NotificationPermissionStatus.RequiredButNotGranted,
            -> {
                presenter.showNotificationPermissionRequired()
                false
            }
        }
    }
}
