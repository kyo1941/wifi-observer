package com.example.wifi_observer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun NetworkScreen(
    viewModel: NetworkViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        NetworkStatusView(networkStatus = uiState.value.networkStatus)
        NetworkCheckButtons(viewModel::getNetworkStatus)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
fun NetworkScreenPreview() {
    NetworkScreen(viewModel = NetworkViewModel())
}