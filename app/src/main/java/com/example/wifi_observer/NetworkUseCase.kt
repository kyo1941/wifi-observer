package com.example.wifi_observer

import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.platform.interfaces.NetworkConnectivity
import com.example.wifi_observer.viewmodel.NetworkUiStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * 取得したネットワーク状態を UI モデルに変換し、エラーハンドリングを行うクラス
 */
class NetworkUseCase(
    private val networkConnectivity: NetworkConnectivity
) {
    fun observeNetworkStatus(): Flow<NetworkUiStatus> {
        // TODO: Wifi →　Mobile の時にプッシュ通知を表示する
        return networkConnectivity.observeNetworkStatus().map { it.toUiStatus() }
    }

    suspend fun getCurrentNetworkStatus(): NetworkUiStatus {
        return networkConnectivity.observeNetworkStatus().firstOrNull()?.toUiStatus()
            ?: NetworkUiStatus.Error("まだネットワーク状態が取得できていません。もう少しお待ちください。")
    }

    private fun Result<NetworkStatus>.toUiStatus(): NetworkUiStatus {
        return fold(
            onSuccess = { status ->
                when (status) {
                    is NetworkStatus.Connected -> when (status.type) {
                        NetworkStatus.NetworkType.Wifi -> NetworkUiStatus.Wifi
                        NetworkStatus.NetworkType.Mobile -> NetworkUiStatus.Mobile
                        NetworkStatus.NetworkType.Other -> NetworkUiStatus.Other
                    }

                    is NetworkStatus.NotConnected -> NetworkUiStatus.NotConnected
                }
            },
            onFailure = {
                NetworkUiStatus.Error("ネットワーク状態の取得に失敗しました")
            }
        )
    }
}