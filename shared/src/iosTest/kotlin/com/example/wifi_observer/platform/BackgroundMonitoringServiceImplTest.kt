package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.model.NetworkStatus
import com.example.wifi_observer.domain.usecase.NetworkUseCase
import com.example.wifi_observer.fake.FakeNetworkConnectivity
import com.example.wifi_observer.fake.FakeNetworkNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundMonitoringServiceImplTest {
    private val wifi = NetworkStatus.Connected(NetworkStatus.NetworkType.Wifi)
    private val mobile = NetworkStatus.Connected(NetworkStatus.NetworkType.Mobile)

    private fun newService(notifier: FakeNetworkNotifier = FakeNetworkNotifier()) =
        BackgroundMonitoringServiceImpl(NetworkUseCase(FakeNetworkConnectivity()), notifier)

    private class Fixture(
        scope: TestScope,
    ) {
        val connectivity = FakeNetworkConnectivity()
        val notifier = FakeNetworkNotifier()

        init {
            val useCase = NetworkUseCase(connectivity, TestTimeSource())
            val service = BackgroundMonitoringServiceImpl(useCase, notifier)
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                service.observeBatch()
            }
        }

        suspend fun emit(status: NetworkStatus) = connectivity.emit(Result.success(status))
    }

    @BeforeTest
    @AfterTest
    fun clearMonitoringFlag() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(BackgroundMonitoringServiceImpl.MONITORING_KEY)
    }

    @Test
    fun `バッチ実行で Wifi から Mobile への切り替えを検知すると通知する`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.emit(mobile)

            assertEquals(1, fixture.notifier.notifyCount)
        }

    @Test
    fun `バッチ実行で切り替えが起きなければ通知しない`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)

            assertEquals(0, fixture.notifier.notifyCount)
        }

    @Test
    fun `監視中フラグが未設定なら監視していないと判定する`() {
        assertFalse(newService().isMonitoring)
    }

    @Test
    fun `監視中フラグが保存されていれば監視中と判定する`() {
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = BackgroundMonitoringServiceImpl.MONITORING_KEY)

        assertTrue(newService().isMonitoring)
    }

    /**
     * テストホストでは submitTaskRequest が失敗して予約が作られず、タスクの投入状態が再現できないため予約の取り消しは検証しない。
     */
    @Test
    fun `stop すると監視中フラグを落とす`() {
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = BackgroundMonitoringServiceImpl.MONITORING_KEY)
        val service = newService()

        service.stop()

        assertFalse(service.isMonitoring)
    }

    @Test
    fun `stop すると実行中のバッチを止める`() {
        val service = newService()
        val batchJob = service.launchObserveBatch()

        service.stop()

        assertTrue(batchJob.isCancelled)
    }
}
