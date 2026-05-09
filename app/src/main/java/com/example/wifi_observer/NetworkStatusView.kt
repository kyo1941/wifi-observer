package com.example.wifi_observer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun NetworkStatusView(
    modifier: Modifier = Modifier,
    networkStatus: NetworkStatus
) {
    Text(
        text = when(networkStatus) {
            NetworkStatus.Wifi -> "Wifi"
            NetworkStatus.Mobile -> "Mobile"
            NetworkStatus.Disconnected -> "Disconnected"
            NetworkStatus.Unknown -> "Unknown"
        },
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
