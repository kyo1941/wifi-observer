package com.example.wifi_observer.components.network

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * ネットワーク表示のアイコン
 *
 * Material3 Icon をラップしてサイズをしているだけ
 */
@Composable
fun NetworkIconOnly(
    modifier: Modifier = Modifier,
    painter: Painter,
    tint: Color = LocalContentColor.current
) {
    Icon(
        modifier = modifier.size(120.dp),
        painter = painter,
        contentDescription = null,
        tint = tint
    )
}
