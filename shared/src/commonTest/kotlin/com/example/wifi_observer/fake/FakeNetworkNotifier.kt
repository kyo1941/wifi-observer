package com.example.wifi_observer.fake

import com.example.wifi_observer.domain.gateway.NetworkNotifier

/**
 * 通知発火の回数を記録する Fake
 */
internal class FakeNetworkNotifier : NetworkNotifier {
    var notifyCount = 0
        private set

    override fun notifyWifiToMobile() {
        notifyCount++
    }
}
