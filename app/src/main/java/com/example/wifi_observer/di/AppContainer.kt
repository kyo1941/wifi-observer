package com.example.wifi_observer.di

import android.content.Context
import android.net.ConnectivityManager
import com.example.wifi_observer.NetworkUseCase
import com.example.wifi_observer.platform.NetworkConnectivityImpl
import com.example.wifi_observer.platform.interfaces.NetworkConnectivity

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

    val networkUseCase: NetworkUseCase by lazy {
        NetworkUseCase(networkConnectivity)
    }
}
