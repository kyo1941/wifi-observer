package com.example.wifi_observer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifi_observer.viewmodel.NetworkViewModel
import com.example.wifi_observer.viewmodel.UiState

@Composable
fun NetworkScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        NetworkScreenSwitcher(viewModel = viewModel)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun NetworkScreenSwitcher(
    viewModel: NetworkViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState.value) {
        is UiState.Init -> NetworkInitialView(onClick = viewModel::observeNetworkStatus)
        is UiState.Ready -> NetworkContentView(
            networkStatus = state.networkStatus,
            onStartObserve = viewModel::observeNetworkStatus,
            onStopObserve = viewModel::stopObserveNetworkStatus
        )
    }
}

@Preview
@Composable
fun NetworkScreenPreview() {
    // TODO: ViewModel の依存を剥がすために stateless の Composable に分割する
    // NetworkScreen(modifier = Modifier.background(Color.White))
}