// app/src/main/java/com/example/logleaf/data/settings/TimeSettings.kt
package com.example.logleaf.data.settings

import java.time.DayOfWeek

data class TimeSettings(
    val dayStartHour: Int = 0,
    val dayStartMinute: Int = 0,
    val weekStartDay: DayOfWeek = DayOfWeek.SUNDAY,
    val timeFormat: TimeFormat = TimeFormat.TWENTY_FOUR_HOUR
)

enum class TimeFormat(val displayName: String) {
    TWELVE_HOUR("12時間"),
    TWENTY_FOUR_HOUR("24時間")
}

// 週の始まりを日本語表示用
val DayOfWeek.displayName: String
    get() = when (this) {
        DayOfWeek.SUNDAY -> "日曜日"
        DayOfWeek.MONDAY -> "月曜日"
        DayOfWeek.TUESDAY -> "火曜日"
        DayOfWeek.WEDNESDAY -> "水曜日"
        DayOfWeek.THURSDAY -> "木曜日"
        DayOfWeek.FRIDAY -> "金曜日"
        DayOfWeek.SATURDAY -> "土曜日"
    }