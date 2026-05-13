package com.example.wifi_observer.components.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wifi_observer.R

/**
 * ネットワーク画面の操作に関する共通ボタン
 * Icon と Text を縦に並べて表示する
 *
 * @param modifier Modifier
 * @param iconPainter Icon の Painter
 * @param labelText ボタンのラベル
 * @param onClick クリックイベント
 */
@Composable
fun NetworkIconTextButton(
    modifier: Modifier = Modifier,
    iconPainter: Painter,
    labelText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = labelText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

    }
}

@Preview
@Composable
private fun NetworkIconTextButtonPreview() {
    NetworkIconTextButton(
        iconPainter = painterResource(R.drawable.ic_launcher_foreground),
        labelText = "label text",
        onClick = {}
    )
}