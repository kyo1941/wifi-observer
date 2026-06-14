package com.example.wifi_observer

import com.example.wifi_observer.domain.model.NetworkMonitoringStatus
import com.example.wifi_observer.domain.model.NetworkStatus
import com.example.wifi_observer.domain.usecase.NetworkUseCase
import com.example.wifi_observer.fake.FakeNetworkConnectivity
import com.example.wifi_observer.fake.FakeNetworkNotificationPresenter
import com.example.wifi_observer.fake.FakeNetworkStatusPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkUseCaseTest {
    private val wifi = NetworkStatus.Connected(NetworkStatus.NetworkType.Wifi)
    private val mobile = NetworkStatus.Connected(NetworkStatus.NetworkType.Mobile)
    private val notConnected = NetworkStatus.NotConnected

    private class Fixture(
        scope: TestScope,
    ) {
        val timeSource = TestTimeSource()
        val connectivity = FakeNetworkConnectivity()
        val notificationPresenter = FakeNetworkNotificationPresenter()
        val statusPresenter = FakeNetworkStatusPresenter()

        init {
            val useCase = NetworkUseCase(connectivity, timeSource)
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                useCase.observe(notificationPresenter, statusPresenter)
            }
        }

        suspend fun emit(status: NetworkStatus) = connectivity.emit(Result.success(status))
    }

    @Test
    fun `Wifi から Mobile へ直接切り替わると通知する`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.emit(mobile)

            assertEquals(1, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `grace period 内の NotConnected を挟んだ Wifi から Mobile への切り替えは通知する`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.emit(notConnected)
            fixture.timeSource += 2.seconds
            fixture.emit(mobile)

            assertEquals(1, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `grace period を超えた NotConnected の後に Mobile へ接続しても通知しない`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.emit(notConnected)
            fixture.timeSource += 6.seconds
            fixture.emit(mobile)

            assertEquals(0, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `Mobile から NotConnected を挟んで Mobile に戻っても通知しない`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(mobile)
            fixture.emit(notConnected)
            fixture.timeSource += 2.seconds
            fixture.emit(mobile)

            assertEquals(0, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `Wifi 接続歴がないまま Mobile へ接続しても通知しない`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(notConnected)
            fixture.emit(mobile)

            assertEquals(0, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `通知後に NotConnected を挟んで Mobile に戻っても再通知しない`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.emit(notConnected)
            fixture.timeSource += 2.seconds
            fixture.emit(mobile)
            fixture.emit(notConnected)
            fixture.timeSource += 2.seconds
            fixture.emit(mobile)

            assertEquals(1, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `失敗結果を挟んでも Wifi から Mobile への切り替え判定は維持される`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.connectivity.emit(Result.failure(Throwable("Network unavailable")))
            fixture.emit(mobile)

            assertEquals(1, fixture.notificationPresenter.displayCount)
        }

    @Test
    fun `statusPresenter には NotConnected や失敗を含む全状態が渡される`() =
        runTest {
            val fixture = Fixture(this)

            fixture.emit(wifi)
            fixture.emit(notConnected)
            fixture.connectivity.emit(Result.failure(Throwable("Network unavailable")))
            fixture.emit(mobile)

            assertEquals(
                listOf(
                    NetworkMonitoringStatus.Available(wifi),
                    NetworkMonitoringStatus.Available(notConnected),
                    NetworkMonitoringStatus.Failed,
                    NetworkMonitoringStatus.Available(mobile),
                ),
                fixture.statusPresenter.statuses,
            )
        }
}
