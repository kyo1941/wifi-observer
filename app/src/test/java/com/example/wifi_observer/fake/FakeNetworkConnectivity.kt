package com.example.wifi_observer.fake

import com.example.wifi_observer.model.NetworkStatus
import com.example.wifi_observer.platform.interfaces.NetworkConnectivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * テストから任意のタイミングでネットワーク状態を emit できる Fake
 */
internal class FakeNetworkConnectivity : NetworkConnectivity {
    private val channel = Channel<Result<NetworkStatus>>(Channel.UNLIMITED)

    override fun observeNetworkStatus(): Flow<Result<NetworkStatus>> = channel.consumeAsFlow()

    suspend fun emit(result: Result<NetworkStatus>) = channel.send(result)
}
