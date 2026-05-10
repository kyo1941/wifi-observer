package com.example.wifi_observer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun NetworkStatusView(
    modifier: Modifier = Modifier,
    networkStatus: NetworkUiStatus
) {
    Text(
        text = networkStatus.toString(),
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
