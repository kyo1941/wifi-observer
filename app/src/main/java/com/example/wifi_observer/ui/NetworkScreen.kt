package com.example.wifi_observer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifi_observer.R
import com.example.wifi_observer.viewmodel.NetworkViewModel
import com.example.wifi_observer.viewmodel.UiState

@Composable
fun NetworkScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworkViewModel,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundBrush =
        remember(backgroundColor, surfaceVariantColor) {
            Brush.verticalGradient(
                colors =
                    listOf(
                        backgroundColor,
                        surfaceVariantColor.copy(alpha = 0.4f),
                    ),
            )
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppHeader(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 24.dp, end = 24.dp),
        )

        NetworkScreenSwitcher(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            viewModel = viewModel,
        )
    }
}

@Composable
private fun AppHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NetworkScreenSwitcher(
    viewModel: NetworkViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState.value) {
        is UiState.Init ->
            NetworkInitialView(
                modifier = modifier,
                onClick = viewModel::observeNetworkStatus,
            )

        is UiState.Ready ->
            NetworkContentView(
                modifier = modifier,
                networkStatus = state.networkStatus,
                onStartObserve = viewModel::observeNetworkStatus,
                onStopObserve = viewModel::stopObserveNetworkStatus,
            )
    }
}
