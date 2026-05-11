package com.example.wifi_observer

import android.app.Application
import com.example.wifi_observer.di.AppContainer

class WifiObserverApplication: Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}