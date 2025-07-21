package com.example.logleaf

import androidx.compose.ui.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.logleaf.ui.theme.SnsType
import java.time.ZonedDateTime

@Entity(tableName = "posts")
data class Post(
    @PrimaryKey
    val id: String,

    val accountId: String,

    val text: String,
    val createdAt: ZonedDateTime,
    val source: SnsType,

    @ColumnInfo(name = "isHidden", defaultValue = "0")
    val isHidden: Boolean = false
) {
    @delegate:androidx.room.Ignore
    val color: Color by lazy { source.brandColor }
}