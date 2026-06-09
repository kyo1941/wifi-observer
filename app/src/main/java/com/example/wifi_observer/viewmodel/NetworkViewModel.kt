package com.example.wifi_observer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifi_observer.NetworkMonitor
import com.example.wifi_observer.NotificationPermissionUseCase
import com.example.wifi_observer.model.NetworkMonitoringStatus
import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.model.NotificationPermissionRequestResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Init : UiState

    data class Ready(
        val networkStatus: NetworkUiStatus,
    ) : UiState
}

class NetworkViewModel(
    private val networkMonitor: NetworkMonitor,
    private val notificationPermissionUseCase: NotificationPermissionUseCase,
) : ViewModel(),
    NotificationPermissionPresenter {
    private val _uiState = MutableStateFlow<UiState>(UiState.Init)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<NetworkUiEffect>()
    val uiEffect: SharedFlow<NetworkUiEffect> = _uiEffect.asSharedFlow()

    private var isNotificationPermissionRequestInFlight = false

    init {
        viewModelScope.launch {
            networkMonitor.status.collect { status ->
                if (status == null) {
                    resetNetworkUiStatus()
                } else {
                    updateNetworkUiStatus(status.toUiStatus())
                }
            }
        }
    }

    fun observeNetworkStatus() {
        viewModelScope.launch {
            val isMonitoringStartable =
                notificationPermissionUseCase.isMonitoringStartable(this@NetworkViewModel)

            if (isMonitoringStartable) {
                updateNetworkUiStatus(NetworkUiStatus.Loading)
                networkMonitor.start()
            }
        }
    }

    fun stopObserveNetworkStatus() {
        networkMonitor.stop()
        resetNetworkUiStatus()
    }

    fun updateNotificationPermission(result: NotificationPermissionRequestResult) {
        viewModelScope.launch {
            isNotificationPermissionRequestInFlight = false
            val isMonitoringStartable =
                notificationPermissionUseCase.updateNotificationPermission(
                    result = result,
                    presenter = this@NetworkViewModel,
                )

            if (isMonitoringStartable) {
                // NOTE: LoadingのみPlatform側で状態を更新する
                updateNetworkUiStatus(NetworkUiStatus.Loading)
                networkMonitor.start()
            }
        }
    }

    override fun requestNotificationPermission() {
        if (isNotificationPermissionRequestInFlight) return

        isNotificationPermissionRequestInFlight = true
        emitUiEffect(NetworkUiEffect.RequestNotificationPermission)
    }

    override fun showNotificationPermissionRequired() {
        emitUiEffect(NetworkUiEffect.ShowNotificationPermissionRequiredSnackbar)
    }

    private fun emitUiEffect(effect: NetworkUiEffect) {
        viewModelScope.launch {
            _uiEffect.emit(effect)
        }
    }

    private fun updateNetworkUiStatus(status: NetworkUiStatus) {
        _uiState.update { UiState.Ready(status) }
    }

    private fun resetNetworkUiStatus() {
        _uiState.update { UiState.Init }
    }

    private fun NetworkMonitoringStatus.toUiStatus(): NetworkUiStatus =
        when (this) {
            is NetworkMonitoringStatus.Available ->
                when (val networkStatus = status) {
                    is NetworkStatus.Connected ->
                        when (networkStatus.type) {
                            NetworkStatus.NetworkType.Wifi -> NetworkUiStatus.Wifi
                            NetworkStatus.NetworkType.Mobile -> NetworkUiStatus.Mobile
                            NetworkStatus.NetworkType.Other -> NetworkUiStatus.Other
                        }

                    is NetworkStatus.NotConnected -> NetworkUiStatus.NotConnected
                }

            is NetworkMonitoringStatus.Failed ->
                NetworkUiStatus.Error("ネットワーク状態の取得に失敗しました")
        }
}
