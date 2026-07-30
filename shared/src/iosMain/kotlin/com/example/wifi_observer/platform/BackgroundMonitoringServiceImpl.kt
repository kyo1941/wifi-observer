package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.gateway.BackgroundMonitoringService
import com.example.wifi_observer.domain.gateway.NetworkNotificationPresenter
import com.example.wifi_observer.domain.gateway.NetworkNotifier
import com.example.wifi_observer.domain.gateway.NetworkStatusPresenter
import com.example.wifi_observer.domain.model.NetworkMonitoringStatus
import com.example.wifi_observer.domain.usecase.NetworkUseCase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults
import kotlin.concurrent.Volatile

/**
 * BGTaskScheduler により、OS が起こしたタイミングで監視をバッチ実行する。
 * バッチ実行のプロセスには UI 層が接続されず ViewModel が生成されないため、通知の出力ポートはこのクラス自身が担う。
 */
class BackgroundMonitoringServiceImpl(
    private val networkUseCase: NetworkUseCase,
    private val networkNotifier: NetworkNotifier,
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BackgroundMonitoringService,
    NetworkNotificationPresenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = NSLock()
    private val previousTypeStore = PreviousNetworkTypeStore(userDefaults)

    @Volatile
    private var observeJob: Job? = null

    val isMonitoring: Boolean
        get() = userDefaults.boolForKey(MONITORING_KEY)

    fun register() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
            launchHandler = { task -> handleTask(task) },
        )
    }

    /**
     * TODO: 予約に失敗しても呼び出し元に伝える手段がない。共通 interface の見直しを含め、UI への提示は task2 で対応する。
     **/
    override fun start() {
        withStateLock {
            // 予約できていないのに監視中として復元され続けるのを避けるため、成立を確認してから保存する
            if (submitTaskRequest()) {
                userDefaults.setBool(true, forKey = MONITORING_KEY)
            }
        }
    }

    override fun stop() {
        withStateLock {
            userDefaults.setBool(false, forKey = MONITORING_KEY)
            BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
            // 取り消せるのは保留中の予約だけで、実行中のバッチはそのまま検知・通知しうるため明示的に止める
            observeJob?.cancel()
            observeJob = null
            // 停止中の切り替えは観測できないため、再開後に停止前の種別から遷移したと誤判定しないよう基準を捨てる
            previousTypeStore.clear()
        }
    }

    override fun displayNotification() {
        networkNotifier.notifyWifiToMobile()
    }

    internal suspend fun observeBatch() {
        networkUseCase.observe(
            notificationPresenter = this,
            statusPresenter = DiscardingNetworkStatusPresenter,
        )
    }

    internal fun launchObserveBatch(): Job = scope.launch { observeBatch() }.also { observeJob = it }

    private fun handleTask(task: BGTask?) {
        if (task == null) return

        val job =
            withStateLock {
                // stop() 後も OS は投入済みの予約を起こしうるため、ここで監視状態を確認して再投入の連鎖を断つ
                if (isMonitoring) startNextBatchCycle() else null
            }

        if (job == null) {
            task.setTaskCompletedWithSuccess(true)
            return
        }

        task.setExpirationHandler { job.cancel() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null) }
    }

    private fun startNextBatchCycle(): Job {
        // 1件の予約は1回しか実行されない。この実行中にプロセスが落ちても次回が残るよう、観測の完了を待たずに先に予約する
        if (!submitTaskRequest()) {
            // 次回の予約が取れなかった時点で連鎖は途切れ、以後の検知は走らない。監視中を騙り続けないよう状態を降ろす
            userDefaults.setBool(false, forKey = MONITORING_KEY)
        }
        return launchObserveBatch()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun submitTaskRequest(): Boolean =
        BGTaskScheduler.sharedScheduler.submitTaskRequest(BGAppRefreshTaskRequest(TASK_IDENTIFIER), null)

    private inline fun <T> withStateLock(block: () -> T): T {
        stateLock.lock()
        try {
            return block()
        } finally {
            stateLock.unlock()
        }
    }

    companion object {
        const val TASK_IDENTIFIER = "com.example.wifi_observer.network-monitoring-refresh"

        internal const val MONITORING_KEY = "com.example.wifi_observer.is_monitoring"
    }
}

private object DiscardingNetworkStatusPresenter : NetworkStatusPresenter {
    /**
     * iOS ではバッチ実行のプロセスは監視の完了後に破棄されるため、状態を渡す先の UI もメモリ上の保持先も存在しないため no-op
     */
    override fun onNetworkStatusUpdated(status: NetworkMonitoringStatus) = Unit
}
