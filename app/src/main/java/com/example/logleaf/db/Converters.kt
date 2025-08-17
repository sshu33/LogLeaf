package com.example.logleaf.db

import androidx.room.TypeConverter
import com.example.logleaf.ui.theme.SnsType
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    @TypeConverter
    fun fromTimestamp(value: String?): ZonedDateTime? {
        return value?.let {
            return formatter.parse(it, ZonedDateTime::from)
        }
    }

    @TypeConverter
    fun dateToTimestamp(date: ZonedDateTime?): String? {
        return date?.format(formatter)
    }

    @TypeConverter
    fun fromSnsType(value: String): SnsType {
        return when (value) {
            "GOOGLEFIT" -> SnsType.FITBIT
            else -> SnsType.valueOf(value)
        }
    }

    @TypeConverter
    fun snsTypeToString(snsType: SnsType): String {
        return snsType.name
    }
}