package com.example.wifi_observer.ui.components.network

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
 * 円形バッジの中央に表示する内容。
 */
sealed interface BadgeContent {
    /** アイコンを表示する。 */
    data class Icon(
        val painter: Painter,
    ) : BadgeContent

    /** 不定進捗のインジケーターを表示する。 */
    data object Progress : BadgeContent
}

/**
 * 円形バッジ
 *
 * アクセントカラーの同心円の中央に [content] を表示する。
 *
 * @param content 中央に表示する内容（アイコン or 進捗インジケーター）
 * @param accent アクセントカラー
 */
@Composable
fun NetworkStatusBadge(
    content: BadgeContent,
    accent: Color,
    modifier: Modifier = Modifier,
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

        when (content) {
            is BadgeContent.Progress ->
                CircularProgressIndicator(
                    modifier = Modifier.size(96.dp),
                    color = accent,
                    strokeWidth = 6.dp,
                )

            is BadgeContent.Icon ->
                Icon(
                    painter = content.painter,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(92.dp),
                )
        }
    }
}
