package com.example.logleaf.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FitbitHistoryManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("Fitbit_History_Prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_HISTORY_FETCH_TIME = "last_history_fetch_time"
        private const val KEY_OLDEST_DATA_DATE = "oldest_data_date"
        private const val KEY_NEWEST_DATA_DATE = "newest_data_date"
    }

    /**
     * 最後の履歴取得時刻を記録
     */
    fun setLastHistoryFetchTime(userId: String) {
        val currentTime = System.currentTimeMillis()
        prefs.edit()
            .putLong("${KEY_LAST_HISTORY_FETCH_TIME}_$userId", currentTime)
            .apply()

        Log.d("FitbitHistory", "履歴取得時刻記録: userId=$userId, time=$currentTime")
    }

    /**
     * 最後の履歴取得時刻を取得
     */
    fun getLastHistoryFetchTime(userId: String): Long? {
        val time = prefs.getLong("${KEY_LAST_HISTORY_FETCH_TIME}_$userId", -1L)
        return if (time == -1L) null else time
    }

    /**
     * 次回取得可能時刻まで残り時間（ミリ秒）
     */
    fun getTimeUntilNextFetch(userId: String): Long {
        val lastFetchTime = getLastHistoryFetchTime(userId) ?: return 0L
        val oneHourInMillis = 3600000L // 1時間
        val nextFetchTime = lastFetchTime + oneHourInMillis
        val now = System.currentTimeMillis()

        return maxOf(0L, nextFetchTime - now)
    }

    /**
     * 履歴取得が可能かどうか
     */
    fun canFetchHistory(userId: String): Boolean {
        return getTimeUntilNextFetch(userId) == 0L
    }

    /**
     * 最古データ日付を記録
     */
    fun setOldestDataDate(userId: String, date: LocalDate) {
        val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        prefs.edit()
            .putString("${KEY_OLDEST_DATA_DATE}_$userId", dateString)
            .apply()

        Log.d("FitbitHistory", "最古データ日付記録: userId=$userId, date=$dateString")
    }

    /**
     * 最古データ日付を取得
     */
    fun getOldestDataDate(userId: String): LocalDate? {
        val dateString = prefs.getString("${KEY_OLDEST_DATA_DATE}_$userId", null)
        return dateString?.let { LocalDate.parse(it) }
    }

    /**
     * 最新データ日付を記録
     */
    fun setNewestDataDate(userId: String, date: LocalDate) {
        val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        prefs.edit()
            .putString("${KEY_NEWEST_DATA_DATE}_$userId", dateString)
            .apply()

        Log.d("FitbitHistory", "最新データ日付記録: userId=$userId, date=$dateString")
    }

    /**
     * 最新データ日付を取得
     */
    fun getNewestDataDate(userId: String): LocalDate? {
        val dateString = prefs.getString("${KEY_NEWEST_DATA_DATE}_$userId", null)
        return dateString?.let { LocalDate.parse(it) }
    }

    /**
     * 取得可能期間を計算（次に取得できる2ヶ月分）
     */
    fun getAvailablePeriod(userId: String): Pair<LocalDate, LocalDate>? {
        val oldestDate = getOldestDataDate(userId) ?: return null
        val endDate = oldestDate.minusDays(1) // 最古データの前日まで
        val startDate = endDate.minusMonths(2) // 2ヶ月前から

        return Pair(startDate, endDate)
    }

    /**
     * 初回連携時の期間を記録
     */
    fun recordInitialPeriod(userId: String, startDate: LocalDate, endDate: LocalDate) {
        setOldestDataDate(userId, startDate)
        setNewestDataDate(userId, endDate)

        Log.d("FitbitHistory", "初回期間記録: userId=$userId, $startDate～$endDate")
    }

    /**
     * 履歴取得時の期間を記録
     */
    fun recordHistoryPeriod(userId: String, startDate: LocalDate) {
        // 最古データ日付を更新（より古い日付に）
        setOldestDataDate(userId, startDate)

        Log.d("FitbitHistory", "履歴期間記録: userId=$userId, 最古日付を$startDate に更新")
    }

    /**
     * 残り時間を時:分 形式でフォーマット
     */
    fun formatRemainingTime(userId: String): String {
        val remainingMillis = getTimeUntilNextFetch(userId)
        if (remainingMillis == 0L) return ""

        val remainingMinutes = remainingMillis / 60000
        val hours = remainingMinutes / 60
        val minutes = remainingMinutes % 60

        return "${hours}:${minutes.toString().padStart(2, '0')}"
    }

    /**
     * すべてのデータをクリア（連携解除時）
     */
    fun clearAllData(userId: String) {
        prefs.edit()
            .remove("${KEY_LAST_HISTORY_FETCH_TIME}_$userId")
            .remove("${KEY_OLDEST_DATA_DATE}_$userId")
            .remove("${KEY_NEWEST_DATA_DATE}_$userId")
            .apply()

        Log.d("FitbitHistory", "履歴データクリア: userId=$userId")
    }
}