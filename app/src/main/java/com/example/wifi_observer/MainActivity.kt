package com.example.wifi_observer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wifi_observer.platform.notificationPermissionRequestResult
import com.example.wifi_observer.ui.theme.WifiobserverTheme
import com.example.wifi_observer.viewmodel.NetworkUiEffect
import com.example.wifi_observer.viewmodel.NetworkViewModel
import com.example.wifi_observer.viewmodel.factory.NetworkViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as WifiObserverApplication).appContainer
        val networkViewModelFactory =
            NetworkViewModelFactory(
                appContainer.networkMonitor,
                appContainer.notificationPermissionUseCase,
            )

        enableEdgeToEdge()
        setContent {
            val networkViewModel: NetworkViewModel = viewModel(factory = networkViewModelFactory)
            val snackbarHostState = remember { SnackbarHostState() }
            val notificationPermissionRequiredMessage =
                stringResource(R.string.notification_permission_required)
            val requestNotificationPermission =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { isGranted ->
                    networkViewModel.updateNotificationPermission(
                        notificationPermissionRequestResult(
                            isGranted = isGranted,
                            shouldShowRationale =
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    shouldShowRequestPermissionRationale(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ),
                        ),
                    )
                }

            LaunchedEffect(networkViewModel) {
                var notificationPermissionRequiredSnackbarJob: Job? = null

                networkViewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is NetworkUiEffect.RequestNotificationPermission -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        is NetworkUiEffect.ShowNotificationPermissionRequiredSnackbar -> {
                            notificationPermissionRequiredSnackbarJob?.cancel()
                            snackbarHostState.currentSnackbarData?.dismiss()
                            notificationPermissionRequiredSnackbarJob =
                                launch {
                                    snackbarHostState.showSnackbar(notificationPermissionRequiredMessage)
                                }
                        }
                    }
                }
            }

            WifiobserverTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                ) { innerPadding ->
                    NetworkScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = networkViewModel,
                    )
                }
            }
        }
    }
}
