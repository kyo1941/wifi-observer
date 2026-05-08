package com.example.wifi_observer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NetworkStatus {
    data object Wifi: NetworkStatus
    data object Mobile: NetworkStatus
    data object Disconnected: NetworkStatus
    data object Unknown: NetworkStatus
}

data class UiState(
    val networkStatus: NetworkStatus,
)

class NetworkViewModel: ViewModel() {
    val _uiState = MutableStateFlow(UiState(NetworkStatus.Unknown))
    val uiState = _uiState.asStateFlow()

    fun getNetworkStatus() {
        // TODO: 実際にネットワーク状態を取得する
        viewModelScope.launch {
            if (_uiState.value.networkStatus != NetworkStatus.Wifi) {
                _uiState.update {
                    it.copy(networkStatus = NetworkStatus.Wifi)
                }
            } else {
                _uiState.update {
                    it.copy(networkStatus = NetworkStatus.Mobile)
                }
            }
        }
    }
}
