package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.gateway.NetworkConnectivity
import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
import kotlin.time.Duration.Companion.seconds

/**
 * NWPathMonitor(C API)でネットワーク接続状態の変化を監視する。
 * iOSのプロセスはバッチ実行(BGTaskScheduler)のたびに生成・破棄されるため、その前提の吸収もこのクラスにて行う。
 */
class NetworkConnectivityImpl(
    private val isBatchLaunch: Boolean,
    userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NetworkConnectivity {
    private val sessionStore = MonitoringSessionStore(userDefaults)

    override fun observeNetworkStatus(): Flow<Result<NetworkStatus>> {
        val rawStatus =
            callbackFlow {
                if (isBatchLaunch) {
                    // 前回プロセスが保存した接続種別を Flow 先頭で replay する
                    sessionStore.loadPreviousTypeIfFresh()?.let { trySend(Result.success(NetworkStatus.Connected(it))) }
                }

                val queue = dispatch_queue_create("com.example.wifi_observer.network-monitor", null)
                val monitor = nw_path_monitor_create()
                nw_path_monitor_set_queue(monitor, queue)

                var settleJob: Job? = null
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

                    fun commit() {
                        trySend(Result.success(status))
                        when (status) {
                            is NetworkStatus.Connected -> sessionStore.savePreviousType(status.type)
                            // 非接続を確認できた以上、以前保存した接続種別をそのままreplayに使うと実際にあった切断期間を無視してしまうため、ここで無効化する
                            NetworkStatus.NotConnected -> sessionStore.clearPreviousType()
                        }
                    }

                    if (isBatchLaunch) {
                        // NWPathMonitor は起動直後、確定していない値を連続して返すことがあるため、一定時間発火がなければ最後の値を確定値とみなす(デバウンス)
                        // 確定後は BGTaskScheduler の実行時間制約に収まるよう Flow を完了させる
                        settleJob?.cancel()
                        settleJob =
                            launch {
                                delay(SETTLE_WINDOW)
                                commit()
                                close()
                            }
                    } else {
                        commit()
                    }
                }
                nw_path_monitor_start(monitor)

                awaitClose {
                    settleJob?.cancel()
                    // update handler が ProducerScope(this) を捕捉したまま monitor に保持され続けると 循環参照になりリークするため、cancel の前に参照を明示的に断ち切る
                    nw_path_monitor_set_update_handler(monitor, null)
                    nw_path_monitor_cancel(monitor)
                }
            }
        // isBatchLaunch 時は replay と現在値が偶然一致すると distinctUntilChanged が現在値の emission を握りつぶし「replay 後に現在値を1件emit」の契約が崩れるため適用しない
        return if (isBatchLaunch) rawStatus else rawStatus.distinctUntilChanged()
    }

    companion object {
        /** NWPathMonitor 起動直後の初期発火が安定するまで待つデバウンス時間。 */
        private val SETTLE_WINDOW = 1.seconds
    }
}
