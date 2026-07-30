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

    override fun start() {
        submitTaskRequest()
        userDefaults.setBool(true, forKey = MONITORING_KEY)
    }

    override fun stop() {
        userDefaults.setBool(false, forKey = MONITORING_KEY)
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)

        // 取り消せるのは保留中の予約だけで、実行中のバッチはそのまま検知・通知しうるため明示的に止める
        observeJob?.cancel()
        observeJob = null
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

        // stop() 後も OS は投入済みの予約を起こしうるため、ここで監視状態を確認して再投入の連鎖を断つ
        if (!isMonitoring) {
            task.setTaskCompletedWithSuccess(true)
            return
        }

        // 1件の予約は1回しか実行されない。この実行中にプロセスが落ちても次回が残るよう、観測の完了を待たずに先に予約する
        submitTaskRequest()

        val job = launchObserveBatch()
        task.setExpirationHandler { job.cancel() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun submitTaskRequest() {
        BGTaskScheduler.sharedScheduler.submitTaskRequest(BGAppRefreshTaskRequest(TASK_IDENTIFIER), null)
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
