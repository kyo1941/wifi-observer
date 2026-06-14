package com.example.wifi_observer.fake

import com.example.wifi_observer.domain.gateway.NetworkNotificationPresenter

/**
 * 通知発火の回数を記録する Fake
 */
internal class FakeNetworkNotificationPresenter : NetworkNotificationPresenter {
    var displayCount = 0
        private set

    override fun displayNotification() {
        displayCount++
    }
}
