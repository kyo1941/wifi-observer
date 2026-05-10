package com.example.wifi_observer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface NetworkUiStatus {
    data object Init: NetworkUiStatus
    data object Loading: NetworkUiStatus
    data object Wifi: NetworkUiStatus
    data object Mobile: NetworkUiStatus
    data class Error(val message: String): NetworkUiStatus
}

data class UiState(
    val networkStatus: NetworkUiStatus,
)

class NetworkViewModel: ViewModel() {
    private val _uiState = MutableStateFlow(UiState(networkStatus = NetworkUiStatus.Init))
    val uiState = _uiState.asStateFlow()

    fun getNetworkStatus() {
        // TODO: 実際にネットワーク状態を取得する
        if (_uiState.value.networkStatus != NetworkUiStatus.Wifi) {
            _uiState.update {
                it.copy(networkStatus = NetworkUiStatus.Wifi)
            }
        } else {
            _uiState.update {
                it.copy(networkStatus = NetworkUiStatus.Mobile)
            }
        }
    }
}
