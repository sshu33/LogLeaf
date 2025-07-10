package com.example.logleaf

import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.logleaf.ui.theme.SnsType
import java.time.ZonedDateTime

@Entity(tableName = "posts") // このクラスを"posts"という名前のテーブルとして定義
data class Post(
    @PrimaryKey // これを主キー（各行を一位に識別するID）として定義
    val id: String,

    val text: String,
    val createdAt: ZonedDateTime,
    val source: SnsType
) {
    // データベースに保存する必要はないので、Roomはこれを無視する
    val color: Color
        get() = source.brandColor
}