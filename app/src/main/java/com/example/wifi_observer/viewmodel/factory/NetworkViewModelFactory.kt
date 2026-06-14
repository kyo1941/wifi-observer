package com.example.wifi_observer.viewmodel.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wifi_observer.domain.usecase.NotificationPermissionUseCase
import com.example.wifi_observer.monitor.NetworkMonitor
import com.example.wifi_observer.viewmodel.NetworkViewModel

class NetworkViewModelFactory(
    private val networkMonitor: NetworkMonitor,
    private val notificationPermissionUseCase: NotificationPermissionUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NetworkViewModel::class.java)) {
            return NetworkViewModel(networkMonitor, notificationPermissionUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
