package com.example.wifi_observer.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.wifi_observer.R
import com.example.wifi_observer.platform.interfaces.NetworkNotifier

class NetworkNotifierImpl(
    private val context: Context,
) : NetworkNotifier {
    companion object {
        const val CHANNEL_ID_ALERT = "wifi_to_mobile_alert"
        private const val NOTIFICATION_ID_ALERT = 2
    }

    init {
        createAlertChannel()
    }

    override fun notifyWifiToMobile() {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID_ALERT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("ネットワーク切り替え")
                .setContentText("モバイル回線に切り替わりました")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                CHANNEL_ID_ALERT,
                "WiFi→モバイル通知",
                NotificationManager.IMPORTANCE_HIGH,
            )
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
