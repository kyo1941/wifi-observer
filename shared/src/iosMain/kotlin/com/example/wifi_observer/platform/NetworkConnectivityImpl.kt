package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.gateway.NetworkConnectivity
import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSUserDefaults
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_queue_create

/**
 * NWPathMonitor(C API)でネットワーク接続状態の変化を監視する。
 * iOS はプロセスが毎回新規生成されるバッチ実行モデル(BGTaskScheduler)のため、
 * [isBatchLaunch] が true の場合のみ NSUserDefaults に保存した前回の接続種別を
 * Flow 先頭で replay し、現在値を1件受け取った時点で Flow を完了させる
 * (BGTaskScheduler の実行時間制約に収まるよう、呼び出し側でタイムアウト管理をせずに済ませるため)。
 * 保存自体は isBatchLaunch に関わらず常に行う。
 */
class NetworkConnectivityImpl(
    private val isBatchLaunch: Boolean,
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NetworkConnectivity {
    override fun observeNetworkStatus(): Flow<Result<NetworkStatus>> {
        val rawStatus =
            callbackFlow {
                if (isBatchLaunch) {
                    loadPreviousType()?.let { trySend(Result.success(NetworkStatus.Connected(it))) }
                }

                val queue = dispatch_queue_create("com.example.wifi_observer.network-monitor", null)
                val monitor = nw_path_monitor_create()
                nw_path_monitor_set_queue(monitor, queue)
                nw_path_monitor_set_update_handler(monitor) { path ->
                    val status =
                        if (nw_path_get_status(path) == nw_path_status_satisfied) {
                            val type =
                                when {
                                    nw_path_uses_interface_type(path, nw_interface_type_wifi) ->
                                        NetworkStatus.NetworkType.Wifi
                                    nw_path_uses_interface_type(path, nw_interface_type_cellular) ->
                                        NetworkStatus.NetworkType.Mobile
                                    else -> NetworkStatus.NetworkType.Other
                                }
                            NetworkStatus.Connected(type)
                        } else {
                            NetworkStatus.NotConnected
                        }
                    trySend(Result.success(status))
                    when (status) {
                        is NetworkStatus.Connected -> saveType(status.type)
                        NetworkStatus.NotConnected -> {}
                    }
                    if (isBatchLaunch) close()
                }
                nw_path_monitor_start(monitor)

                awaitClose {
                    // update handler が ProducerScope(this) を捕捉したまま monitor に保持され続けると
                    // 循環参照になりリークするため、cancel の前に参照を明示的に断ち切る
                    nw_path_monitor_set_update_handler(monitor, null)
                    nw_path_monitor_cancel(monitor)
                }
            }
        // isBatchLaunch 時は replay と現在値が偶然一致すると distinctUntilChanged が
        // 現在値の emission を握りつぶし「replay 後に現在値を1件emit」の契約が崩れるため適用しない
        return if (isBatchLaunch) rawStatus else rawStatus.distinctUntilChanged()
    }

    private fun loadPreviousType(): NetworkStatus.NetworkType? = userDefaults.stringForKey(PREVIOUS_TYPE_KEY)?.toNetworkType()

    private fun saveType(type: NetworkStatus.NetworkType) {
        userDefaults.setObject(type.toStorageValue(), forKey = PREVIOUS_TYPE_KEY)
    }

    companion object {
        internal const val PREVIOUS_TYPE_KEY = "com.example.wifi_observer.previous_network_type"
    }
}

internal fun NetworkStatus.NetworkType.toStorageValue(): String =
    when (this) {
        NetworkStatus.NetworkType.Wifi -> "wifi"
        NetworkStatus.NetworkType.Mobile -> "mobile"
        NetworkStatus.NetworkType.Other -> "other"
    }

internal fun String.toNetworkType(): NetworkStatus.NetworkType? =
    when (this) {
        "wifi" -> NetworkStatus.NetworkType.Wifi
        "mobile" -> NetworkStatus.NetworkType.Mobile
        "other" -> NetworkStatus.NetworkType.Other
        else -> null
    }
