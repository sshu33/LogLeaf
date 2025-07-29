package com.example.logleaf.db

import android.util.Log
import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.logleaf.HashtagExtractor
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

    // ▼▼▼ この関数を、丸ごと置き換えてください ▼▼▼
    fun searchPostsWithAnd(keywords: List<String>, visibleAccountIds: List<String>, includeHidden: Int): Flow<List<Post>> {
        if (keywords.isEmpty()) {
            return getAllPosts(visibleAccountIds, includeHidden)
        }

        val finalVisibleIds = visibleAccountIds + "LOGLEAF_INTERNAL_POST"
        val queryBuilder = StringBuilder()
        val args = mutableListOf<Any>()

        // SELECT句：重複を除外して投稿を取得
        queryBuilder.append("SELECT DISTINCT P.* FROM posts AS P ")
        // JOIN句：投稿とタグを結合
        queryBuilder.append("LEFT JOIN post_tag_cross_ref AS PTC ON P.id = PTC.postId ")
        queryBuilder.append("LEFT JOIN tags AS T ON PTC.tagId = T.tagId ")

        // WHERE句：アカウントIDのフィルタ
        queryBuilder.append("WHERE P.accountId IN (")
        queryBuilder.append(finalVisibleIds.joinToString(",") { "?" })
        queryBuilder.append(") ")
        args.addAll(finalVisibleIds)

        // WHERE句：キーワード条件（本文 OR タグ名）
        queryBuilder.append("AND (")
        // 各キーワードについて、本文 OR タグ名の条件を作成
        queryBuilder.append(keywords.joinToString(separator = " AND ") {
            "(P.text LIKE ? OR T.tagName LIKE ?)"
        })
        queryBuilder.append(") ")
        // 引数をキーワードの数 x 2個追加
        keywords.forEach { keyword ->
            args.add("%$keyword%") // P.text LIKE ? の部分
            args.add("%$keyword%") // T.tagName LIKE ? の部分
        }

        // WHERE句：非表示条件
        if (includeHidden == 0) {
            queryBuilder.append("AND P.isHidden = 0 ")
        }

        queryBuilder.append("ORDER BY P.createdAt DESC")

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

    /**
     * タグの isFavorite フラグの状態を更新します。
     * @param tagId 更新対象のタグのID
     * @param isFavorite 設定したいお気に入りの状態 (true/false)
     */
    @Query("UPDATE tags SET isFavorite = :isFavorite WHERE tagId = :tagId")
    suspend fun setTagFavoriteStatus(tagId: Long, isFavorite: Boolean)

    /**
     * お気に入りに設定された（isFavorite = true）タグを全て取得します。
     * 結果はタグ名のアルファベット順でソートされます。
     * @return お気に入りタグのリストをFlowで返します。
     */
    @Query("SELECT * FROM tags WHERE isFavorite = 1 ORDER BY tagName ASC")
    fun getFavoriteTags(): Flow<List<Tag>>

    /**
     * よく使われているタグ（お気に入りを除く）を取得します。
     * 投稿に紐づけられている回数が多い順に、最大10件まで取得します。
     * @return よく使うタグのリストをFlowで返します。
     */
    @Query("""
        SELECT T.* FROM tags AS T
        INNER JOIN post_tag_cross_ref AS PTC ON T.tagId = PTC.tagId
        WHERE T.isFavorite = 0
        GROUP BY T.tagId
        ORDER BY COUNT(T.tagId) DESC
        LIMIT 10
    """)
    fun getFrequentlyUsedTags(): Flow<List<Tag>>

    /**
     * 指定されたタグ名を持つ投稿を検索します。
     * 内部的に、まずタグ名からタグIDを検索し、そのタグIDが付けられた投稿をすべて取得します。
     */
    @Transaction
    @Query("""
        SELECT P.* FROM posts AS P
        INNER JOIN post_tag_cross_ref AS PTC ON P.id = PTC.postId
        INNER JOIN tags AS T ON PTC.tagId = T.tagId
        WHERE T.tagName = :tagName
        AND (P.accountId IN (:visibleAccountIds) OR P.accountId = 'LOGLEAF_INTERNAL_POST')
        AND P.isHidden = 0
        ORDER BY P.createdAt DESC
    """)
    fun searchPostsByTag(tagName: String, visibleAccountIds: List<String>): Flow<List<Post>>

    /**
     * 投稿IDからタグ情報を取得する（検索結果用）
     */
    @Query("""
        SELECT T.* FROM tags AS T
        INNER JOIN post_tag_cross_ref AS PTC ON T.tagId = PTC.tagId
        WHERE PTC.postId = :postId
    """)
    suspend fun getTagsForPost(postId: String): List<Tag>

    /**
     * 投稿にハッシュタグを自動抽出して関連付けます
     *
     * @param post 対象の投稿
     * @return 抽出されたタグの数
     */
    suspend fun extractAndSaveHashtags(post: Post): Int {

        val hashtags = HashtagExtractor.extractHashtags(post.text)
        if (hashtags.isEmpty()) return 0

        // 1. 抽出したハッシュタグをタグテーブルに保存
        val tagIds = mutableListOf<Long>()
        hashtags.forEach { tagName ->
            var tagId = insertTag(Tag(tagName = tagName))
            if (tagId == -1L) {
                // 既存のタグの場合、IDを取得
                tagId = getTagIdByName(tagName) ?: 0L
            }
            if (tagId != 0L) {
                tagIds.add(tagId)
            }
        }

        // 2. 投稿とタグの関連付けを保存（重複は自動で無視される）
        tagIds.forEach { tagId ->
            insertPostTagCrossRef(PostTagCrossRef(post.id, tagId))
        }

        return hashtags.size
    }

    /**
     * 投稿を保存し、同時にハッシュタグを自動抽出します
     * 新規投稿・SNS投稿取得時に使用
     *
     * @param post 保存する投稿
     * @return 抽出されたタグの数
     */
    suspend fun insertWithHashtagExtraction(post: Post): Int {
        // 1. 投稿を保存
        insert(post)

        // 2. ハッシュタグを自動抽出・保存
        return extractAndSaveHashtags(post)
    }

    /**
     * 投稿IDから投稿を取得（存在チェック用）
     */
    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: String): Post?

    /**
     * 複数の投稿を保存し、新規投稿のみハッシュタグを自動抽出します
     * 修正版：既存投稿の重複抽出を防ぐ
     */
    suspend fun insertAllWithHashtagExtraction(posts: List<Post>): Pair<Int, Int> {
        var newPostCount = 0
        var totalTagsExtracted = 0

        posts.forEach { post ->
            // 既存投稿かチェック
            val existingPost = getPostById(post.id)
            val isNewPost = existingPost == null

            // 投稿を保存（新規・更新どちらでも）
            insert(post)

            // 新規投稿のみハッシュタグ抽出
            if (isNewPost) {
                newPostCount++
                val tagsCount = extractAndSaveHashtags(post)
                totalTagsExtracted += tagsCount
                Log.d("HashtagDebug", "新規投稿 ${post.id} から${tagsCount}個のタグを抽出")
            } else {
                Log.d("HashtagDebug", "既存投稿 ${post.id} はスキップ")
            }
        }

        Log.d("HashtagDebug", "結果: ${posts.size}件中${newPostCount}件が新規、合計${totalTagsExtracted}個のタグを抽出")
        return Pair(newPostCount, totalTagsExtracted)
    }

    /**
     * 全ての既存投稿にハッシュタグ抽出を一括適用します
     * 初回導入時・メンテナンス時に使用
     *
     * @return 処理した投稿数とタグ数のPair
     */
    @Query("SELECT * FROM posts")
    suspend fun getAllPostsForMaintenance(): List<Post>

    suspend fun applyHashtagExtractionToAllPosts(): Pair<Int, Int> {
        val allPosts = getAllPostsForMaintenance()
        var totalTagsExtracted = 0

        allPosts.forEach { post ->
            val tagsCount = extractAndSaveHashtags(post)
            totalTagsExtracted += tagsCount
        }

        return Pair(allPosts.size, totalTagsExtracted)
    }
}