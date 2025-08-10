// app/src/main/java/com/example/logleaf/data/settings/TimeSettingsRepository.kt
package com.example.logleaf.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

class TimeSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "time_settings",
        Context.MODE_PRIVATE
    )

    private val _timeSettings = MutableStateFlow(loadSettings())
    val timeSettings: StateFlow<TimeSettings> = _timeSettings.asStateFlow()

    private fun loadSettings(): TimeSettings {
        return TimeSettings(
            dayStartHour = prefs.getInt(KEY_DAY_START_HOUR, 0),
            dayStartMinute = prefs.getInt(KEY_DAY_START_MINUTE, 0),
            weekStartDay = DayOfWeek.of(prefs.getInt(KEY_WEEK_START_DAY, 7)), // 7 = SUNDAY
            timeFormat = if (prefs.getBoolean(KEY_TIME_FORMAT_24H, true)) {
                TimeFormat.TWENTY_FOUR_HOUR
            } else {
                TimeFormat.TWELVE_HOUR
            }
        )
    }

    fun updateDayStartTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_DAY_START_HOUR, hour)
            .putInt(KEY_DAY_START_MINUTE, minute)
            .apply()

        _timeSettings.value = _timeSettings.value.copy(
            dayStartHour = hour,
            dayStartMinute = minute
        )
    }

    fun updateWeekStartDay(dayOfWeek: DayOfWeek) {
        prefs.edit()
            .putInt(KEY_WEEK_START_DAY, dayOfWeek.value)
            .apply()

        _timeSettings.value = _timeSettings.value.copy(weekStartDay = dayOfWeek)
    }

    fun updateTimeFormat(format: TimeFormat) {
        prefs.edit()
            .putBoolean(KEY_TIME_FORMAT_24H, format == TimeFormat.TWENTY_FOUR_HOUR)
            .apply()

        _timeSettings.value = _timeSettings.value.copy(timeFormat = format)
    }

    companion object {
        private const val KEY_DAY_START_HOUR = "day_start_hour"
        private const val KEY_DAY_START_MINUTE = "day_start_minute"
        private const val KEY_WEEK_START_DAY = "week_start_day"
        private const val KEY_TIME_FORMAT_24H = "time_format_24h"
    }
}