package com.example.logleaf.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.logleaf.Post
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<Post>)

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<Post>>

    /**
     * 複数のキーワードすべてを含む投稿を検索する (AND検索)
     * @param keywords 検索キーワードのリスト
     * @return 検索結果の投稿リスト
     */
    @Transaction // 複数のクエリを安全に実行するためのアノテーション
    @Query("""
    SELECT * FROM posts
    WHERE
        -- この部分は動的に生成されるので、クエリの本体はWHERE 1=1で始める
        1=1
""")
    fun searchPostsWithAnd(keywords: List<String>): Flow<List<Post>> {
        // 基本となるクエリ
        var query = "SELECT * FROM posts WHERE "
        // 各キーワードに対して "text LIKE ?" という条件を " AND " で連結していく
        query += keywords.joinToString(separator = " AND ") { "text LIKE ?" }
        // 最後に並び順を指定
        query += " ORDER BY createdAt DESC"

        // 各キーワードの前後に'%'を付けたものを引数として渡す
        val args = keywords.map { "%$it%" }.toTypedArray()

        // RoomのRawQueryを使って、動的に生成したクエリを実行する
        return SimpleSQLiteQuery(query, args).let {
            searchPostsWithAndRaw(it)
        }
    }

    // 動的に生成したクエリを受け取って実行するための、ヘルパー関数
    @RawQuery(observedEntities = [Post::class])
    fun searchPostsWithAndRaw(query: SimpleSQLiteQuery): Flow<List<Post>>

// -----------------------------------------------------------------
// ★★★ ここまでが新しい部分です ★★★
// -----------------------------------------------------------------


// 古いsearchPostsはもう不要ですが、念のためコメントアウトして残しておきます
//    @Query("""
//        SELECT * FROM posts
//        WHERE text LIKE :query
//        ORDER BY createdAt DESC
//    """)
//    fun searchPosts(query: String): Flow<List<Post>>

    @Query("DELETE FROM posts")
    suspend fun deleteAll()
}