package com.example.logleaf.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.logleaf.Post

@Database(entities = [Post::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao

    // ↓↓↓ この companion object ブロックを丸ごと追加してください ↓↓↓
    companion object {
        // @Volatile をつけることで、INSTANCEへの書き込みが他のスレッドから即座に見えるようになる
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // INSTANCEがnullなら、新しいインスタンスを生成する。
            // nullでなければ、既存のインスタンスを返す。(シングルトン)
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "logleaf_database" // データベースファイル名
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
    // ↑↑↑ ここまで ↑↑↑
}