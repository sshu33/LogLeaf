package com.example.logleaf.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.logleaf.Post

// データベースのバージョンを2に更新
@Database(entities = [Post::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * バージョン1から2へのマイグレーション定義。
         * postsテーブルにaccountIdカラムを追加します。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // postsテーブルにaccountIdカラム(TEXT型、NULL不許可)を追加。
                // 既存のデータには、デフォルト値として空文字('')を設定します。
                database.execSQL("ALTER TABLE posts ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "logleaf_database" // データベースファイル名
                )
                    .addMigrations(MIGRATION_1_2) // ◀️ 定義したマイグレーションをビルダーに追加
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
