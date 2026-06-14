package com.example.wifi_observer.di

import android.content.Context
import android.net.ConnectivityManager
import com.example.wifi_observer.domain.gateway.BackgroundMonitoringService
import com.example.wifi_observer.domain.gateway.NetworkConnectivity
import com.example.wifi_observer.domain.gateway.NetworkNotifier
import com.example.wifi_observer.domain.gateway.NotificationPermissionRepository
import com.example.wifi_observer.domain.usecase.NetworkUseCase
import com.example.wifi_observer.domain.usecase.NotificationPermissionUseCase
import com.example.wifi_observer.monitor.NetworkMonitor
import com.example.wifi_observer.platform.ForegroundMonitoringServiceController
import com.example.wifi_observer.platform.NetworkConnectivityImpl
import com.example.wifi_observer.platform.NetworkNotifierImpl
import com.example.wifi_observer.platform.NotificationPermissionRepositoryImpl

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val connectivityManager: ConnectivityManager by lazy {
        appContext.getSystemService(ConnectivityManager::class.java)
    }

    private val networkConnectivity: NetworkConnectivity by lazy {
        NetworkConnectivityImpl(connectivityManager)
    }

    private val networkNotifier: NetworkNotifier by lazy {
        NetworkNotifierImpl(appContext)
    }

    private val networkUseCase: NetworkUseCase by lazy {
        NetworkUseCase(networkConnectivity)
    }

    private val backgroundMonitoringService: BackgroundMonitoringService by lazy {
        ForegroundMonitoringServiceController(appContext)
    }

    private val notificationPermissionRepository: NotificationPermissionRepository by lazy {
        NotificationPermissionRepositoryImpl(appContext)
    }

    val notificationPermissionUseCase: NotificationPermissionUseCase by lazy {
        NotificationPermissionUseCase(notificationPermissionRepository)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(networkUseCase, networkNotifier, backgroundMonitoringService)
    }
}
