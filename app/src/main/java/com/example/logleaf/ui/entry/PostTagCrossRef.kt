package com.example.logleaf.ui.entry

import androidx.room.Entity

@Entity(tableName = "post_tag_cross_ref", primaryKeys = ["postId", "tagId"])
data class PostTagCrossRef(
    val postId: String,
    val tagId: Long
)