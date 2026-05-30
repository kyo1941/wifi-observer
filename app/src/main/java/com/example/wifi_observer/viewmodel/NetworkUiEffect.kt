package com.example.wifi_observer.viewmodel

sealed interface NetworkUiEffect {
    data object RequestNotificationPermission : NetworkUiEffect

    data object ShowNotificationPermissionRequiredSnackbar : NetworkUiEffect
}
