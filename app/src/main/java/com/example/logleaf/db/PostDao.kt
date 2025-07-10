package com.example.logleaf.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.logleaf.Post
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<Post>)

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<Post>>

    @Query("""
        SELECT * FROM posts
        WHERE text LIKE :query
        ORDER BY createdAt DESC
    """)
    fun searchPosts(query: String): Flow<List<Post>>
    @Query("DELETE FROM posts")
    suspend fun deleteAll()
}