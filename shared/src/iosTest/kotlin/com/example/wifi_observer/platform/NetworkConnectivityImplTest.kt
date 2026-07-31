package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * 実機の NWPathMonitor が返す現在のネットワーク種別はテスト環境依存で確定できないため、
 * ここでは isBatchLaunch による replay の有無・emission 件数・永続化の発生という構造的な振る舞いのみを検証する。
 */
class NetworkConnectivityImplTest {
    // Mac / iOS シミュレータには携帯回線インターフェースが存在しないため、実測値が偶然この値と一致することは実質的にない前提で replay 検出の目印に使う。
    private val previousTypeMarker = NetworkStatus.NetworkType.Mobile

    private val sessionStore = MonitoringSessionStore(NSUserDefaults.standardUserDefaults)

    /**
     * 保存済みの前回値を用意する。replay は今のセッションで保存された値だけを対象とするため、
     * セッションの開始時刻も併せて値より前へずらし、鮮度以外の理由で弾かれないようにする。
     */
    private fun seedPreviousType(
        type: NetworkStatus.NetworkType,
        secondsAgo: Double = 0.0,
    ) {
        val savedAt = NSDate().timeIntervalSince1970 - secondsAgo
        NSUserDefaults.standardUserDefaults.setObject(
            type.toStorageValue(),
            forKey = MonitoringSessionStore.TYPE_KEY,
        )
        NSUserDefaults.standardUserDefaults.setDouble(savedAt, forKey = MonitoringSessionStore.SAVED_AT_KEY)
        NSUserDefaults.standardUserDefaults.setDouble(
            savedAt - 60.0,
            forKey = MonitoringSessionStore.SESSION_STARTED_AT_KEY,
        )
    }

    @BeforeTest
    fun beginSession() {
        sessionStore.endSession()
        sessionStore.beginSession()
    }

    @AfterTest
    fun clearPersistedState() {
        sessionStore.endSession()
    }

    @Test
    fun `isBatchLaunch が false なら保存済みの前回値があっても replay しない`() =
        runTest {
            seedPreviousType(previousTypeMarker)

            val onlyEmission = NetworkConnectivityImpl(isBatchLaunch = false).observeNetworkStatus().first()

            assertNotEquals(Result.success(NetworkStatus.Connected(previousTypeMarker)), onlyEmission)
        }

    @Test
    fun `isBatchLaunch が true でも保存済みの前回値がなければ replay せず現在値のみで完了する`() =
        runTest(timeout = 5.seconds) {
            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()

            assertEquals(1, emissions.size)
        }

    @Test
    fun `isBatchLaunch が true かつ保存済みの前回値が新しければ replay 後に現在値を emit して完了する`() =
        runTest(timeout = 5.seconds) {
            seedPreviousType(previousTypeMarker)

            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()

            assertEquals(2, emissions.size)
            assertEquals(Result.success(NetworkStatus.Connected(previousTypeMarker)), emissions.first())
        }

    @Test
    fun `isBatchLaunch が true でも保存済みの前回値が古すぎれば replay しない`() =
        runTest(timeout = 5.seconds) {
            // MonitoringSessionStore の STALENESS_THRESHOLD(15分)を確実に超える値
            seedPreviousType(previousTypeMarker, secondsAgo = 1.hours.inWholeSeconds.toDouble())

            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()

            assertEquals(1, emissions.size)
        }

    @Test
    fun `isBatchLaunch が true でも時刻が巻き戻っていれば replay しない`() =
        runTest(timeout = 5.seconds) {
            // 巻き戻ると保存時刻が現在より後になり、経過時間が負になる。それが期限切れ判定をすり抜けないことを見る
            seedPreviousType(previousTypeMarker, secondsAgo = -1.hours.inWholeSeconds.toDouble())

            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()

            assertEquals(1, emissions.size)
        }

    @Test
    fun `現在値の取得後は種別が NSUserDefaults に保存される`() =
        runTest(timeout = 5.seconds) {
            // take(1) で即キャンセルすると saveType（別スレッドの update handler 内）と
            // 読み取りの間に競合しうるため、close() 経由で完了するまで待って完了を保証する
            val emissions = NetworkConnectivityImpl(isBatchLaunch = true).observeNetworkStatus().toList()
            val current = emissions.first()

            val savedType =
                NSUserDefaults.standardUserDefaults
                    .stringForKey(
                        MonitoringSessionStore.TYPE_KEY,
                    )?.toNetworkType()
            val currentStatus = current.getOrThrow()
            assertIs<NetworkStatus.Connected>(currentStatus)
            assertEquals(currentStatus.type, savedType)
        }
}
