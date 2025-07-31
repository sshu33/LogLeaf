package com.example.logleaf.ui.entry

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "post_images")
data class PostImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postId: String,
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val orderIndex: Int
)