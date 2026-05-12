package com.example.wifi_observer

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.wifi_observer.components.network.NetworkIconOnly
import com.example.wifi_observer.components.network.NetworkIconTextButton
import com.example.wifi_observer.viewmodel.NetworkUiStatus

@Composable
fun NetworkContentView(
    networkStatus: NetworkUiStatus,
    onStartObserve: () -> Unit,
    onStopObserve: () -> Unit,
) {
    when (networkStatus) {
        is NetworkUiStatus.Loading -> {
            NetworkActionLayout(
                topContent = {
                    CircularProgressIndicator()
                },
                actionButton = {
                    NetworkIconTextButton(
                        iconPainter = painterResource(R.drawable.baseline_stop_24),
                        labelText = stringResource(R.string.stop),
                        onClick = onStopObserve
                    )
                }
            )
        }

        is NetworkUiStatus.Wifi, NetworkUiStatus.Mobile, NetworkUiStatus.Other, NetworkUiStatus.NotConnected -> {
            NetworkActionLayout(
                topContent = {
                    NetworkIconOnly(
                        painter = networkStatus.toIcon()
                    )
                },
                actionButton = {
                    NetworkIconTextButton(
                        iconPainter = painterResource(R.drawable.baseline_stop_24),
                        labelText = stringResource(R.string.stop),
                        onClick = onStopObserve
                    )
                }
            )
        }

        is NetworkUiStatus.Error -> {
            NetworkActionLayout(
                topContent = {
                    NetworkIconOnly(
                        painter = networkStatus.toIcon()
                    )
                },
                middleContent = {
                    Text(text = networkStatus.message)
                },
                actionButton = {
                    NetworkIconTextButton(
                        iconPainter = painterResource(R.drawable.baseline_autorenew_24),
                        labelText = stringResource(R.string.retry),
                        onClick = onStartObserve
                    )
                }
            )
        }
    }
}

@Composable
fun NetworkUiStatus.toIcon(): Painter {
    return when (this) {
        is NetworkUiStatus.Wifi -> painterResource(R.drawable.outline_android_wifi_3_bar_24)
        is NetworkUiStatus.Mobile -> painterResource(R.drawable.outline_cell_wifi_24)
        is NetworkUiStatus.Other -> painterResource(R.drawable.outline_globe_24)
        is NetworkUiStatus.NotConnected -> painterResource(R.drawable.outline_globe_2_cancel_24)
        is NetworkUiStatus.Error -> painterResource(R.drawable.outline_globe_2_question_24)

        // NOTE: ローディング時はアイコンを使用しない
        is NetworkUiStatus.Loading -> painterResource(R.drawable.outline_forward_circle_24)
    }
}
