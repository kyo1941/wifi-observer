package com.example.wifi_observer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wifi_observer.ui.theme.WifiobserverTheme
import com.example.wifi_observer.viewmodel.NetworkViewModel
import com.example.wifi_observer.viewmodel.factory.NetworkViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as WifiObserverApplication).appContainer
        val networkViewModelFactory = NetworkViewModelFactory(appContainer.networkUseCase)

        enableEdgeToEdge()
        setContent {
            val networkViewModel: NetworkViewModel = viewModel(factory = networkViewModelFactory)
            WifiobserverTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NetworkScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = networkViewModel,
                    )
                }
            }
        }
    }
}
