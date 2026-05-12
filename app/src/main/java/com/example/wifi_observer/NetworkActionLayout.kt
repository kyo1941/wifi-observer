package com.example.wifi_observer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val NetworkTopContentSize = 120.dp
private val NetworkTopContentBottomSpacing = 144.dp

@Composable
fun NetworkActionLayout(
    modifier: Modifier = Modifier,
    topContent: @Composable () -> Unit,
    middleContent: @Composable () -> Unit = {},
    actionButton: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(NetworkTopContentSize),
            contentAlignment = Alignment.Center,
        ) {
            topContent()
        }
        middleContent()
        Spacer(modifier = Modifier.height(NetworkTopContentBottomSpacing))
        actionButton()
    }
}
