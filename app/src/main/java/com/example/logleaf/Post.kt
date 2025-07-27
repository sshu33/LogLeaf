package com.example.logleaf

import androidx.compose.ui.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.logleaf.ui.entry.PostTagCrossRef
import com.example.logleaf.ui.entry.Tag
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
    val imageUrl: String?,
    @ColumnInfo(name = "isHidden", defaultValue = "0")
    val isHidden: Boolean = false
) {
    @delegate:androidx.room.Ignore
    val color: Color by lazy { source.brandColor }
}

data class PostWithTags(
    @Embedded val post: Post,
    @Relation(
        parentColumn = "id",       // Post側のキー (Post.id)
        entityColumn = "tagId",    // Tag側のキー (Tag.tagId)
        associateBy = Junction( // ◀◀ @マークを削除
            value = PostTagCrossRef::class,
            parentColumn = "postId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag>
)