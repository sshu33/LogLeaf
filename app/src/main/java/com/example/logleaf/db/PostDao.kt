package com.example.logleaf.db

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.logleaf.Post
import com.example.logleaf.PostWithTags
import com.example.logleaf.ui.entry.PostTagCrossRef
import com.example.logleaf.ui.entry.Tag
import com.example.logleaf.ui.theme.SnsType
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<Post>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: Post)

    /**
     * 1件の投稿を更新する。編集機能で使用。
     */
    @Update
    suspend fun update(post: Post)

    /**
     * 投稿の表示／非表示の状態だけを効率的に更新する。
     */
    @Query("UPDATE posts SET isHidden = :isHidden WHERE id = :postId")
    suspend fun setPostHiddenStatus(postId: String, isHidden: Boolean)

    /**
     * IDを指定して1件の投稿を削除する。
     */
    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePostById(postId: String)

    @Query("""
    SELECT * FROM posts
    WHERE accountId IN (:visibleAccountIds)
    AND (isHidden = 0 OR :includeHidden = 1)
    ORDER BY createdAt DESC
""")
    fun getAllPosts(visibleAccountIds: List<String>, includeHidden: Int): Flow<List<Post>>

    // in PostDao.kt
    @Query("""
    SELECT * FROM posts
    WHERE date(createdAt) = :dateString
    AND accountId IN (:visibleAccountIds)
    AND (isHidden = 0 OR :includeHidden = 1)
    ORDER BY createdAt ASC
""")
    fun getPostsForDate(dateString: String, visibleAccountIds: List<String>, includeHidden: Int): Flow<List<Post>>

    fun searchPostsWithAnd(keywords: List<String>, visibleAccountIds: List<String>, includeHidden: Int): Flow<List<Post>> {
        if (keywords.isEmpty()) {
            return getAllPosts(visibleAccountIds, includeHidden)
        }

        // ViewModel側でLOGLEAF用のIDも渡すようにしたので、ここでもIDリストを準備
        val finalVisibleIds = visibleAccountIds + "LOGLEAF_INTERNAL_POST"

        val queryBuilder = StringBuilder()
        val args = mutableListOf<Any>()

        // 条件句: WHERE accountId IN (...)
        queryBuilder.append("SELECT * FROM posts WHERE accountId IN (")
        queryBuilder.append(finalVisibleIds.joinToString(",") { "?" })
        queryBuilder.append(") ")
        args.addAll(finalVisibleIds)

        // 条件句: AND (キーワード条件)
        queryBuilder.append("AND (")
        queryBuilder.append(keywords.joinToString(separator = " AND ") { "text LIKE ?" })
        queryBuilder.append(") ")
        args.addAll(keywords.map { "%$it%" })

        // 条件句: AND (非表示条件)
        if (includeHidden == 0) {
            queryBuilder.append("AND isHidden = 0 ")
        }

        queryBuilder.append("ORDER BY createdAt DESC")

        return searchPostsWithAndRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }


    @RawQuery(observedEntities = [Post::class])
    fun searchPostsWithAndRaw(query: SimpleSQLiteQuery): Flow<List<Post>>


    @Query("DELETE FROM posts WHERE accountId = :accountId")
    suspend fun deletePostsByAccountId(accountId: String)

    @Query("DELETE FROM posts")
    suspend fun deleteAll()

    /**
     * 新しいタグを `tags` テーブルに一件追加します。
     * 同じ名前のタグが既にあれば、何もしません（IGNORE）。
     * @return 新しく挿入されたタグのID（tagId）を返します。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag): Long

    /**
     * 投稿とタグの関連付けを一件追加します。
     * @param crossRef どの投稿(postId)とどのタグ(tagId)を結びつけるかの情報
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPostTagCrossRef(crossRef: PostTagCrossRef)

    /**
     * 特定の投稿に紐づくタグの関連付けを全て削除します。
     * 投稿のタグを編集する際に、一度リセットするために使います。
     */
    @Query("DELETE FROM post_tag_cross_ref WHERE postId = :postId")
    suspend fun deletePostTagCrossRefs(postId: String)

    /**
     * 投稿とその投稿に紐づく全てのタグを取得します。
     * @Transaction アノテーションにより、投稿の取得とタグの取得が
     * 安全な単一のデータベース処理として実行されることを保証します。
     */
    @Transaction
    @Query("SELECT * FROM posts")
    fun getPostsWithTags(): Flow<List<PostWithTags>>

    @Query("SELECT tagId FROM tags WHERE tagName = :name")
    suspend fun getTagIdByName(name: String): Long?

    @Transaction
    suspend fun updatePostWithTags(post: Post, tags: List<PostTagCrossRef>) {
        // 1. 投稿を保存 (新規または更新)
        insert(post)
        // 2. 古いタグの関連付けを全て削除
        deletePostTagCrossRefs(post.id)
        // 3. 新しいタグの関連付けを全て追加
        tags.forEach { insertPostTagCrossRef(it) }
    }
}