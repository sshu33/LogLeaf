package com.example.logleaf

import androidx.compose.ui.graphics.Color
import com.example.logleaf.ui.theme.SnsType
import java.time.ZonedDateTime


data class Post(

    val id: String,
    val text: String,
    val createdAt: ZonedDateTime,
    val source: SnsType
) {
    val color: Color
        get() = source.brandColor
}