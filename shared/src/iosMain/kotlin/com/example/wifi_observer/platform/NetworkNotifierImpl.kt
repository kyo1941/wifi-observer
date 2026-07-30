package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.gateway.NetworkNotifier
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * UNUserNotificationCenter で Wifi → モバイル切り替えをローカル通知する。
 */
class NetworkNotifierImpl(
    private val notificationCenter: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : NetworkNotifier {
    override fun notifyWifiToMobile() {
        val content =
            UNMutableNotificationContent().apply {
                setTitle("ネットワーク切り替え")
                setBody("モバイル回線に切り替わりました")
                setSound(UNNotificationSound.defaultSound)
            }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = REQUEST_IDENTIFIER,
            content = content,
            trigger = null,
        )

        notificationCenter.addNotificationRequest(request, null)
    }

    companion object {
        private const val REQUEST_IDENTIFIER = "com.example.wifi_observer.wifi_to_mobile_alert"
    }
}
