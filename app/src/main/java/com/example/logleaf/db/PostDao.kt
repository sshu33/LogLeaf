package com.example.logleaf.db

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.logleaf.Post
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<Post>)

    @Query("""
        SELECT * FROM posts
        WHERE accountId IN (:visibleAccountIds)
        ORDER BY createdAt DESC
    """)
    fun getAllPosts(visibleAccountIds: List<String>): Flow<List<Post>>

    /**
     * ▼ 変更点: @Transactionアノテーションを削除します。
     * この関数はクエリを組み立てるだけで、直接DBアクセスする複数の処理をまとめている訳ではないため、@Transactionは不要であり、エラーの原因でした。
     */
    fun searchPostsWithAnd(keywords: List<String>, visibleAccountIds: List<String>): Flow<List<Post>> {
        // キーワードが空の場合は、全表示対象投稿を返す
        if (keywords.any { it.isBlank() }) {
            return getAllPosts(visibleAccountIds)
        }

        // 表示対象アカウントの中から、さらにキーワードで絞り込む
        val queryBuilder = StringBuilder()
        queryBuilder.append("SELECT * FROM posts WHERE accountId IN (")
        // accountIdのプレースホルダを追加
        visibleAccountIds.forEachIndexed { index, _ ->
            queryBuilder.append("?")
            if (index < visibleAccountIds.size - 1) queryBuilder.append(",")
        }
        queryBuilder.append(") AND (")
        // キーワードのプレースホルダを追加
        queryBuilder.append(keywords.joinToString(separator = " AND ") { "text LIKE ?" })
        queryBuilder.append(") ORDER BY createdAt DESC")

        // 引数リストを作成（アカウントID + キーワード）
        val args = visibleAccountIds.toMutableList()
        args.addAll(keywords.map { "%$it%" })

        return searchPostsWithAndRaw(SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray()))
    }

    @RawQuery(observedEntities = [Post::class])
    fun searchPostsWithAndRaw(query: SimpleSQLiteQuery): Flow<List<Post>>


    @Query("DELETE FROM posts WHERE accountId = :accountId")
    suspend fun deletePostsByAccountId(accountId: String)

    @Query("DELETE FROM posts")
    suspend fun deleteAll()
}