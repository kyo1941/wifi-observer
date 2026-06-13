package com.example.wifi_observer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.wifi_observer.components.network.BadgeContent
import com.example.wifi_observer.components.network.NetworkIconTextButton
import com.example.wifi_observer.components.network.NetworkStatusBadge
import com.example.wifi_observer.ui.theme.StatusConnected
import com.example.wifi_observer.ui.theme.StatusError
import com.example.wifi_observer.ui.theme.StatusMobile
import com.example.wifi_observer.ui.theme.StatusOther
import com.example.wifi_observer.ui.theme.StatusWarning
import com.example.wifi_observer.viewmodel.NetworkUiStatus

@Composable
fun NetworkContentView(
    networkStatus: NetworkUiStatus,
    onStartObserve: () -> Unit,
    onStopObserve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = networkStatus.accentColor()

    val (title, description) =
        when (networkStatus) {
            is NetworkUiStatus.Loading ->
                stringResource(R.string.status_loading_title) to
                    stringResource(R.string.status_loading_description)

            is NetworkUiStatus.Wifi ->
                stringResource(R.string.status_wifi_title) to
                    stringResource(R.string.status_wifi_description)

            is NetworkUiStatus.Mobile ->
                stringResource(R.string.status_mobile_title) to
                    stringResource(R.string.status_mobile_description)

            is NetworkUiStatus.Other ->
                stringResource(R.string.status_other_title) to
                    stringResource(R.string.status_other_description)

            is NetworkUiStatus.NotConnected ->
                stringResource(R.string.status_disconnected_title) to
                    stringResource(R.string.status_disconnected_description)

            is NetworkUiStatus.Error ->
                stringResource(R.string.status_error_title) to networkStatus.message
        }

    NetworkActionLayout(
        modifier = modifier,
        badge = {
            NetworkStatusBadge(
                content = networkStatus.toBadgeContent(),
                accent = accent,
            )
        },
        title = title,
        description = description,
        actionButton = {
            if (networkStatus is NetworkUiStatus.Error) {
                NetworkIconTextButton(
                    iconPainter = painterResource(R.drawable.baseline_autorenew_24),
                    labelText = stringResource(R.string.retry),
                    onClick = onStartObserve,
                )
            } else {
                NetworkIconTextButton(
                    iconPainter = painterResource(R.drawable.baseline_stop_24),
                    labelText = stringResource(R.string.stop),
                    onClick = onStopObserve,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    )
}

@Composable
private fun NetworkUiStatus.accentColor(): Color =
    when (this) {
        is NetworkUiStatus.Wifi -> StatusConnected
        is NetworkUiStatus.Mobile -> StatusMobile
        is NetworkUiStatus.Other -> StatusOther
        is NetworkUiStatus.NotConnected -> StatusWarning
        is NetworkUiStatus.Error -> StatusError
        is NetworkUiStatus.Loading -> MaterialTheme.colorScheme.primary
    }

@Composable
fun NetworkUiStatus.toBadgeContent(): BadgeContent =
    when (this) {
        is NetworkUiStatus.Wifi -> BadgeContent.Icon(painterResource(R.drawable.outline_android_wifi_3_bar_24))
        is NetworkUiStatus.Mobile -> BadgeContent.Icon(painterResource(R.drawable.outline_cell_wifi_24))
        is NetworkUiStatus.Other -> BadgeContent.Icon(painterResource(R.drawable.outline_globe_24))
        is NetworkUiStatus.NotConnected -> BadgeContent.Icon(painterResource(R.drawable.outline_globe_2_cancel_24))
        is NetworkUiStatus.Error -> BadgeContent.Icon(painterResource(R.drawable.outline_globe_2_question_24))

        is NetworkUiStatus.Loading -> BadgeContent.Progress
    }
