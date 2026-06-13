package com.example.wifi_observer.components.network

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifi_observer.R

/**
 * ネットワーク画面の操作に関する共通ボタン
 * Icon と Text を横に並べた、角丸（ピル型）のボタン
 *
 * @param modifier Modifier
 * @param iconPainter Icon の Painter
 * @param labelText ボタンのラベル
 * @param onClick クリックイベント
 * @param containerColor 背景色
 * @param contentColor アイコン・テキストの色
 */
@Composable
fun NetworkIconTextButton(
    modifier: Modifier = Modifier,
    iconPainter: Painter,
    labelText: String,
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 3.dp,
                pressedElevation = 1.dp,
            ),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 16.dp),
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = labelText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview
@Composable
private fun NetworkIconTextButtonPreview() {
    NetworkIconTextButton(
        iconPainter = painterResource(R.drawable.baseline_play_arrow_24),
        labelText = "開始",
        onClick = {},
    )
}
