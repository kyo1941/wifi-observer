package com.example.wifi_observer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.wifi_observer.components.network.BadgeContent
import com.example.wifi_observer.components.network.NetworkIconTextButton
import com.example.wifi_observer.components.network.NetworkStatusBadge

@Composable
fun NetworkInitialView(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NetworkActionLayout(
        modifier = modifier,
        badge = {
            NetworkStatusBadge(
                content = BadgeContent.Icon(painterResource(R.drawable.outline_android_wifi_3_bar_24)),
                accent = MaterialTheme.colorScheme.outline,
            )
        },
        title = stringResource(R.string.status_idle_title),
        description = stringResource(R.string.status_idle_description),
        actionButton = {
            NetworkIconTextButton(
                iconPainter = painterResource(R.drawable.baseline_play_arrow_24),
                labelText = stringResource(R.string.start),
                onClick = onClick,
            )
        },
    )
}
