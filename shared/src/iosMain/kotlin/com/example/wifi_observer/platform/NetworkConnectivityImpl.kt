package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.gateway.NetworkConnectivity
import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.posix.time
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * NWPathMonitor(C API)でネットワーク接続状態の変化を監視する。
 * iOSのプロセスはバッチ実行(BGTaskScheduler)のたびに生成・破棄されるため、その前提の吸収もこのクラスにて行う。
 */
class NetworkConnectivityImpl(
    private val isBatchLaunch: Boolean,
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NetworkConnectivity {
    override fun observeNetworkStatus(): Flow<Result<NetworkStatus>> {
        val rawStatus =
            callbackFlow {
                if (isBatchLaunch) {
                    // 前回プロセスが保存した接続種別を Flow 先頭で replay する
                    loadPreviousTypeIfFresh()?.let { trySend(Result.success(NetworkStatus.Connected(it))) }
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
                            is NetworkStatus.Connected -> saveType(status.type)
                            // 非接続を確認できた以上、以前保存した接続種別をそのままreplayに使うと実際にあった切断期間を無視してしまうため、ここで無効化する
                            NetworkStatus.NotConnected -> clearPreviousType()
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

    private fun loadPreviousTypeIfFresh(): NetworkStatus.NetworkType? {
        if (userDefaults.objectForKey(PREVIOUS_TYPE_SAVED_AT_KEY) == null) {
            return null
        }

        val savedAt = userDefaults.doubleForKey(PREVIOUS_TYPE_SAVED_AT_KEY)
        val elapsed = (nowEpochSeconds() - savedAt).seconds

        if (elapsed > REPLAY_STALENESS_THRESHOLD) {
            return null
        }

        return userDefaults.stringForKey(PREVIOUS_TYPE_KEY)?.toNetworkType()
    }

    private fun saveType(type: NetworkStatus.NetworkType) {
        userDefaults.setObject(type.toStorageValue(), forKey = PREVIOUS_TYPE_KEY)
        userDefaults.setDouble(nowEpochSeconds(), forKey = PREVIOUS_TYPE_SAVED_AT_KEY)
    }

    private fun clearPreviousType() {
        userDefaults.removeObjectForKey(PREVIOUS_TYPE_KEY)
        userDefaults.removeObjectForKey(PREVIOUS_TYPE_SAVED_AT_KEY)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun nowEpochSeconds(): Double = time(null).toDouble()

    companion object {
        internal const val PREVIOUS_TYPE_KEY = "com.example.wifi_observer.previous_network_type"
        internal const val PREVIOUS_TYPE_SAVED_AT_KEY = "com.example.wifi_observer.previous_network_type_saved_at"

        // NWPathMonitor起動直後の初期発火が安定するまで待つデバウンス時間
        private val SETTLE_WINDOW = 1.seconds

        // 別バッチにて保存された前回の接続種別をreplayとして信用してよい期間。
        // NotConnectedの観測時点でclearPreviousType()により無効化されるため、この閾値が実際に効くのは「切断が一度も観測されないまま時間が経過した」場合のみ。
        // BGTaskSchedulerの実起動間隔(OS裁量で15分〜数時間、それ以上空くこともある)に閾値を合わせるのではなく、
        // 「これより古い情報を"たった今起きたこと"として通知するのは意味がない」というアプリ側の基準として決め、OSがこの時間内に起動しなければ 検知を諦める(通知しない)という意図的なトレードオフとする。
        private val REPLAY_STALENESS_THRESHOLD = 15.minutes
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
