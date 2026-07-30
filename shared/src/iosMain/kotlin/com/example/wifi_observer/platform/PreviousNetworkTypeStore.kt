package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.model.NetworkStatus
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults
import platform.posix.time
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 直前に観測した接続種別を保持する。
 *
 * iOS ではバッチ実行のたびにプロセスが作り直され、遷移の判定に必要な直前の値がメモリ上に残らないため永続化する。
 */
internal class PreviousNetworkTypeStore(
    private val userDefaults: NSUserDefaults,
) {
    fun loadIfFresh(): NetworkStatus.NetworkType? {
        if (userDefaults.objectForKey(SAVED_AT_KEY) == null) {
            return null
        }

        val savedAt = userDefaults.doubleForKey(SAVED_AT_KEY)
        val elapsed = (nowEpochSeconds() - savedAt).seconds

        if (elapsed > STALENESS_THRESHOLD) {
            return null
        }

        return userDefaults.stringForKey(TYPE_KEY)?.toNetworkType()
    }

    fun save(type: NetworkStatus.NetworkType) {
        userDefaults.setObject(type.toStorageValue(), forKey = TYPE_KEY)
        userDefaults.setDouble(nowEpochSeconds(), forKey = SAVED_AT_KEY)
    }

    fun clear() {
        userDefaults.removeObjectForKey(TYPE_KEY)
        userDefaults.removeObjectForKey(SAVED_AT_KEY)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun nowEpochSeconds(): Double = time(null).toDouble()

    companion object {
        internal const val TYPE_KEY = "com.example.wifi_observer.previous_network_type"
        internal const val SAVED_AT_KEY = "com.example.wifi_observer.previous_network_type_saved_at"

        /**
         * BGTaskScheduler の起動間隔に合わせて延ばしてはいけない。
         *
         * これより古い観測を「たった今の切り替え」として通知しても意味がないためこの値にしており、起動が間に合わなければ検知を諦める。
         */
        private val STALENESS_THRESHOLD = 15.minutes
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
