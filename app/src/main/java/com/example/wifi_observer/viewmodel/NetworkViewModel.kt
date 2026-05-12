package com.example.wifi_observer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifi_observer.NetworkUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NetworkUiStatus {
    data object Loading : NetworkUiStatus
    data object Wifi : NetworkUiStatus
    data object Mobile : NetworkUiStatus
    data object Other : NetworkUiStatus
    data object NotConnected : NetworkUiStatus
    data class Error(val message: String) : NetworkUiStatus
}

sealed interface UiState {
    data object Init : UiState
    data class Ready(val networkStatus: NetworkUiStatus) : UiState
}

class NetworkViewModel(
    private val networkUseCase: NetworkUseCase
) : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Init)
    val uiState = _uiState.asStateFlow()

    private var networkObserveJob: Job? = null

    fun getNetworkStatus() {
        // TODO: 実際にネットワーク状態を取得する
    }

    fun observeNetworkStatus() {
        if (networkObserveJob?.isActive == true) return

        _uiState.value = UiState.Ready(networkStatus = NetworkUiStatus.Loading)
        networkObserveJob = viewModelScope.launch {
            networkUseCase.observeNetworkStatus().collect { status ->
                _uiState.update { current ->
                    when (current) {
                        is UiState.Ready -> current.copy(networkStatus = status)
                        is UiState.Init -> UiState.Ready(status)
                    }
                }

                if (status is NetworkUiStatus.Error) {
                    networkObserveJob?.cancel()
                    /**
                     * エラーが発生した場合は監視を停止して、再監視のための UI に更新する
                     * TODO: ここで Snackbar を表示して、そこからも再試行をできるようにする
                     */
                }
            }
        }
    }

    fun stopObserveNetworkStatus() {
        networkObserveJob?.cancel()
        _uiState.update { UiState.Init }
    }
}
