package com.example.wifi_observer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.wifi_observer.components.network.NetworkIconTextButton

@Composable
fun NetworkInitialView(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    NetworkActionLayout(
        modifier = modifier,
        topContent = {},
        actionButton = {
            NetworkIconTextButton(
                iconPainter = painterResource(R.drawable.baseline_play_arrow_24),
                labelText = stringResource(R.string.start),
                onClick = onClick,
            )
        },
    )
}
