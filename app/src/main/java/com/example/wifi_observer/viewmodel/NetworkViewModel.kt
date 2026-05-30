package com.example.wifi_observer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifi_observer.NetworkMonitor
import com.example.wifi_observer.NotificationPermissionUseCase
import com.example.wifi_observer.model.NetworkMonitoringStatus
import com.example.wifi_observer.model.NetworkStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val uiState: StateFlow<UiState> = networkMonitor.status
        .map { status ->
            status?.let { UiState.Ready(it.toUiStatus()) } ?: UiState.Init
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Init)

    private val _uiEffect = MutableSharedFlow<NetworkUiEffect>()
    val uiEffect: SharedFlow<NetworkUiEffect> = _uiEffect.asSharedFlow()

    private var isNotificationPermissionRequestInFlight = false

    fun observeNetworkStatus() {
        viewModelScope.launch {
            val isMonitoringStartable =
                notificationPermissionUseCase.isMonitoringStartable(this@NetworkViewModel)

            if (isMonitoringStartable) {
                networkMonitor.start()
            }
        }
    }

    fun stopObserveNetworkStatus() {
        networkMonitor.stop()
    }

    fun updateNotificationPermission(isGranted: Boolean) {
        viewModelScope.launch {
            isNotificationPermissionRequestInFlight = false
            val isMonitoringStartable =
                notificationPermissionUseCase.updateNotificationPermission(
                    isGranted = isGranted,
                    presenter = this@NetworkViewModel,
                )

            if (isMonitoringStartable) {
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

    private fun NetworkMonitoringStatus.toUiStatus(): NetworkUiStatus =
        when (this) {
            is NetworkMonitoringStatus.Available -> status.toUiStatus()
            is NetworkMonitoringStatus.Failed ->
                NetworkUiStatus.Error("ネットワーク状態の取得に失敗しました")
        }

    private fun NetworkStatus.toUiStatus(): NetworkUiStatus =
        when (this) {
            is NetworkStatus.Connected ->
                when (type) {
                    NetworkStatus.NetworkType.Wifi -> NetworkUiStatus.Wifi
                    NetworkStatus.NetworkType.Mobile -> NetworkUiStatus.Mobile
                    NetworkStatus.NetworkType.Other -> NetworkUiStatus.Other
                }

            is NetworkStatus.NotConnected -> NetworkUiStatus.NotConnected
        }
}
