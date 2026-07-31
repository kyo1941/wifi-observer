package com.example.wifi_observer.platform

import com.example.wifi_observer.domain.model.NetworkStatus
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 監視セッションと、そのセッション中に直前に観測した接続種別を保持する。
 *
 * iOS ではバッチ実行のたびにプロセスが作り直され、遷移の判定に必要な直前の値がメモリ上に残らないため永続化する。
 */
internal class MonitoringSessionStore(
    private val userDefaults: NSUserDefaults,
) {
    val isSessionActive: Boolean
        get() = userDefaults.objectForKey(SESSION_STARTED_AT_KEY) != null

    fun beginSession() {
        userDefaults.setDouble(nowEpochSeconds(), forKey = SESSION_STARTED_AT_KEY)
    }

    fun endSession() {
        userDefaults.removeObjectForKey(SESSION_STARTED_AT_KEY)
        clearPreviousType()
    }

    fun loadPreviousTypeIfFresh(): NetworkStatus.NetworkType? {
        if (!isSessionActive || userDefaults.objectForKey(SAVED_AT_KEY) == null) {
            return null
        }

        val savedAt = userDefaults.doubleForKey(SAVED_AT_KEY)

        // 終了処理が届かない経路(プロセスの終了、Swift が回す前面監視の観測)では基準値が残りうるため、今のセッションで保存された値かをここでも確かめる
        if (savedAt < userDefaults.doubleForKey(SESSION_STARTED_AT_KEY)) {
            return null
        }

        // 端末の時計が保存後に巻き戻ると経過時間が負になり、そのままでは時計が追いつくまで期限切れにならない
        val elapsed = (nowEpochSeconds() - savedAt).seconds
        if (elapsed.isNegative() || elapsed > STALENESS_THRESHOLD) {
            return null
        }

        return userDefaults.stringForKey(TYPE_KEY)?.toNetworkType()
    }

    fun savePreviousType(type: NetworkStatus.NetworkType) {
        userDefaults.setObject(type.toStorageValue(), forKey = TYPE_KEY)
        userDefaults.setDouble(nowEpochSeconds(), forKey = SAVED_AT_KEY)
    }

    fun clearPreviousType() {
        userDefaults.removeObjectForKey(TYPE_KEY)
        userDefaults.removeObjectForKey(SAVED_AT_KEY)
    }

    // セッションの開始と保存が同じ秒に収まると前後関係を判定できないため、秒未満まで持つ時刻を使う
    private fun nowEpochSeconds(): Double = NSDate().timeIntervalSince1970

    companion object {
        internal const val SESSION_STARTED_AT_KEY = "com.example.wifi_observer.monitoring_session_started_at"
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
