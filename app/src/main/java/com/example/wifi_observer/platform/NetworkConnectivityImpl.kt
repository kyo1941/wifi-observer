package com.example.wifi_observer.platform

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.platform.interfaces.NetworkConnectivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * ネットワークの接続状態の変化を監視するクラス
 * ConnectivityManager.NetworkCallback を継承して、Flow でネットワーク状態を通知する
 */
class NetworkConnectivityImpl(
    private val connectivityManager: ConnectivityManager,
) : NetworkConnectivity {
    override fun observeNetworkStatus(): Flow<Result<NetworkStatus>> =
        callbackFlow {
            val networkCallback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(Result.success(networkCapabilities.toNetworkStatus()))
                    }

                    override fun onLost(network: Network) {
                        trySend(Result.success(NetworkStatus.NotConnected))
                    }

                    override fun onUnavailable() {
                        // FIXME: 発生しうるエラー種別を確認して、適切に catch する
                        trySend(Result.failure(Throwable("Network unavailable")))
                    }
                }
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            awaitClose { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }.distinctUntilChanged()

    /**
     * ドメインモデルへのマッパー
     */
    private fun NetworkCapabilities.toNetworkStatus(): NetworkStatus =
        when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                NetworkStatus.Connected(
                    NetworkStatus.NetworkType.Wifi,
                )
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                NetworkStatus.Connected(
                    NetworkStatus.NetworkType.Mobile,
                )
            else ->
                NetworkStatus.Connected(
                    NetworkStatus.NetworkType.Other,
                )
        }
}
