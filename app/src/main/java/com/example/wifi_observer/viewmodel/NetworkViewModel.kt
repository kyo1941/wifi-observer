package com.example.wifi_observer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifi_observer.NetworkMonitor
import com.example.wifi_observer.model.NetworkStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface UiState {
    data object Init : UiState

    data class Ready(
        val networkStatus: NetworkUiStatus,
    ) : UiState
}

class NetworkViewModel(
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    val uiState: StateFlow<UiState> = networkMonitor.status
        .map { status ->
            status?.let { UiState.Ready(it.toUiStatus()) } ?: UiState.Init
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Init)

    fun observeNetworkStatus() {
        networkMonitor.start()
    }

    fun stopObserveNetworkStatus() {
        networkMonitor.stop()
    }

    private fun Result<NetworkStatus>.toUiStatus(): NetworkUiStatus =
        fold(
            onSuccess = { status ->
                when (status) {
                    is NetworkStatus.Connected ->
                        when (status.type) {
                            NetworkStatus.NetworkType.Wifi -> NetworkUiStatus.Wifi
                            NetworkStatus.NetworkType.Mobile -> NetworkUiStatus.Mobile
                            NetworkStatus.NetworkType.Other -> NetworkUiStatus.Other
                        }

                    is NetworkStatus.NotConnected -> NetworkUiStatus.NotConnected
                }
            },
            onFailure = {
                // TODO: Snackbar で表示して再試行できるようにする
                NetworkUiStatus.Error("ネットワーク状態の取得に失敗しました")
            },
        )
}
