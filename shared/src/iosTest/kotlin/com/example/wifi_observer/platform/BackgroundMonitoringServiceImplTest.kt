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
import kotlin.test.assertNull
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
            // バッチは監視セッション中にしか走らないため、その前提を作ってから観測を始める
            MonitoringSessionStore(NSUserDefaults.standardUserDefaults).beginSession()
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                service.observeBatch()
            }
        }

        suspend fun emit(status: NetworkStatus) = connectivity.emit(Result.success(status))
    }

    private val sessionStore = MonitoringSessionStore(NSUserDefaults.standardUserDefaults)

    @BeforeTest
    @AfterTest
    fun clearPersistedState() {
        sessionStore.endSession()
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
    fun `セッションが始まっていなければ監視していないと判定する`() {
        assertFalse(newService().isMonitoring)
    }

    @Test
    fun `セッションが始まっていれば監視中と判定する`() {
        sessionStore.beginSession()

        assertTrue(newService().isMonitoring)
    }

    /**
     * テストホストでは submitTaskRequest が常に失敗するため、予約が拒否される状況をそのまま再現できる。
     */
    @Test
    fun `予約が拒否されれば監視中にしない`() {
        val service = newService()

        service.start()

        assertFalse(service.isMonitoring)
    }

    /**
     * テストホストでは submitTaskRequest が失敗して予約が作られず、タスクの投入状態が再現できないため予約の取り消しは検証しない。
     */
    @Test
    fun `stop するとセッションを終える`() {
        sessionStore.beginSession()
        val service = newService()

        service.stop()

        assertFalse(service.isMonitoring)
    }

    @Test
    fun `stop すると次回の遷移判定に使う基準値を捨てる`() {
        sessionStore.beginSession()
        sessionStore.savePreviousType(NetworkStatus.NetworkType.Wifi)
        val service = newService()

        service.stop()

        sessionStore.beginSession()
        assertNull(sessionStore.loadPreviousTypeIfFresh())
    }

    /**
     * 終了処理が届かない経路(プロセスの終了、Swift が回す前面監視)で基準値が残った場合の保険が効いているかを見る。
     */
    @Test
    fun `前のセッションで保存された基準値は新しいセッションでは使わない`() {
        sessionStore.beginSession()
        sessionStore.savePreviousType(NetworkStatus.NetworkType.Wifi)

        // endSession を通らずにセッションだけが切り替わった状況
        sessionStore.beginSession()

        assertNull(sessionStore.loadPreviousTypeIfFresh())
    }

    @Test
    fun `セッションが終わっていれば通知しない`() {
        val notifier = FakeNetworkNotifier()
        val service = newService(notifier)

        service.displayNotification()

        assertEquals(0, notifier.notifyCount)
    }

    @Test
    fun `stop すると実行中のバッチを止める`() {
        val service = newService()
        val batchJob = service.launchObserveBatch()

        service.stop()

        assertTrue(batchJob.isCancelled)
    }
}
