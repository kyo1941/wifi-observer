package com.example.wifi_observer.components.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * ネットワーク状態を表す円形バッジ
 *
 * アクセントカラーの同心円（ハロー）の中央にアイコン、
 * もしくはローディング中はプログレスインジケーターを表示する。
 *
 * @param painter 中央に表示するアイコン
 * @param accent 状態に応じたアクセントカラー
 * @param isLoading true の場合はアイコンの代わりにプログレスを表示する
 */
@Composable
fun NetworkStatusBadge(
    painter: Painter,
    accent: Color,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    Box(
        modifier = modifier.size(208.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(208.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.06f)),
        )
        Box(
            modifier =
                Modifier
                    .size(152.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(96.dp),
                color = accent,
                strokeWidth = 6.dp,
            )
        } else {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(92.dp),
            )
        }
    }
}
