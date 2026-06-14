package com.example.wifi_observer.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.wifi_observer.R
import com.example.wifi_observer.WifiObserverApplication
import com.example.wifi_observer.domain.gateway.BackgroundMonitoringService
import com.example.wifi_observer.monitor.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ForegroundMonitoringService : Service() {
    companion object {
        const val CHANNEL_ID_MONITORING = "network_monitoring"
        private const val NOTIFICATION_ID_MONITORING = 1

        fun startIntent(context: Context) = Intent(context, ForegroundMonitoringService::class.java)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate() {
        super.onCreate()
        networkMonitor = (application as WifiObserverApplication).appContainer.networkMonitor
        createMonitoringChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID_MONITORING)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("ネットワーク監視中")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_MONITORING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID_MONITORING, notification)
        }

        if (observeJob?.isActive != true) {
            observeJob =
                serviceScope.launch {
                    networkMonitor.observe()
                }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        observeJob = null
    }

    private fun createMonitoringChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                CHANNEL_ID_MONITORING,
                "ネットワーク監視",
                NotificationManager.IMPORTANCE_MIN,
            )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

class ForegroundMonitoringServiceController(
    private val context: Context,
) : BackgroundMonitoringService {
    override fun start() {
        val intent = ForegroundMonitoringService.startIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stop() {
        context.stopService(ForegroundMonitoringService.startIntent(context))
    }
}
