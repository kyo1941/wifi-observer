package com.example.wifi_observer.model

/**
 * ネットワーク状態を表すドメインモデル
 */
sealed interface NetworkStatus {
    data class Connected(val type: NetworkType): NetworkStatus
    data object NotConnected: NetworkStatus

    /**
     * ネットワーク接続タイプ
     */
    sealed interface NetworkType {
        data object Wifi: NetworkType
        data object Mobile: NetworkType
        data object Other: NetworkType
    }
}
