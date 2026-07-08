package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 実機の NWPathMonitor が返す現在のネットワーク種別はテスト環境依存で確定できないため、
 * ここでは isBatchLaunch による replay の有無・emission 件数・永続化の発生という
 * 構造的な振る舞いのみを検証する。
 */
class NetworkConnectivityImplTest {
    // Mac / iOS シミュレータには携帯回線インターフェースが存在しないため、
    // 実測値が偶然この値と一致することは実質的にない前提で replay 検出の目印に使う。
    private val previousTypeMarker = NetworkStatus.NetworkType.Mobile

    @BeforeTest
    @AfterTest
    fun clearPersistedState() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(NetworkConnectivityImpl.PREVIOUS_TYPE_KEY)
    }

    @Test
    fun `isBatchLaunch が false なら保存済みの前回値があっても replay しない`() =
        runTest {
            NSUserDefaults.standardUserDefaults.setObject(
                previousTypeMarker.toStorageValue(),
                forKey = NetworkConnectivityImpl.PREVIOUS_TYPE_KEY,
            )

            val onlyEmission = NetworkConnectivityImpl(isBatchLaunch = false).observeNetworkStatus().first()

            assertNotEquals(Result.success(NetworkStatus.Connected(previousTypeMarker)), onlyEmission)
        }

    @Test
    fun `isBatchLaunch が true でも保存済みの前回値がなければ replay せず現在値のみで完了する`() =
        runTest {
            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()

            assertEquals(1, emissions.size)
        }

    @Test
    fun `isBatchLaunch が true かつ保存済みの前回値があれば replay 後に現在値を emit して完了する`() =
        runTest {
            NSUserDefaults.standardUserDefaults.setObject(
                previousTypeMarker.toStorageValue(),
                forKey = NetworkConnectivityImpl.PREVIOUS_TYPE_KEY,
            )

            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()

            assertEquals(2, emissions.size)
            assertEquals(Result.success(NetworkStatus.Connected(previousTypeMarker)), emissions.first())
        }

    @Test
    fun `現在値の取得後は種別が NSUserDefaults に保存される`() =
        runTest {
            val current = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().take(1).toList().first()

            val savedType = NSUserDefaults.standardUserDefaults.stringForKey(NetworkConnectivityImpl.PREVIOUS_TYPE_KEY)?.toNetworkType()
            val currentStatus = current.getOrThrow()
            if (currentStatus is NetworkStatus.Connected) {
                assertEquals(currentStatus.type, savedType)
            }
        }
}
