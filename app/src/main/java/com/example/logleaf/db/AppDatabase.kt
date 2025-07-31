package com.example.logleaf.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.logleaf.Post
import com.example.logleaf.ui.entry.PostImage
import com.example.logleaf.ui.entry.PostTagCrossRef
import com.example.logleaf.ui.entry.Tag

@Database(entities = [Post::class, Tag::class, PostTagCrossRef::class, PostImage::class], version = 9, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN accountId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE posts ADD COLUMN imageUrl TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // "tags"テーブルを新しく作成
                database.execSQL("""
                CREATE TABLE IF NOT EXISTS `tags` (
                    `tagId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `tagName` TEXT NOT NULL
                )
            """.trimIndent())

                // "post_tag_cross_ref"テーブルを新しく作成
                database.execSQL("""
                CREATE TABLE IF NOT EXISTS `post_tag_cross_ref` (
                    `postId` TEXT NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    PRIMARY KEY(`postId`, `tagId`)
                )
            """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // "tags"テーブルに、"isFavorite"という名前のカラムを追加します。
                // 型はINTEGER（0=false, 1=true）、空は許さず（NOT NULL）、デフォルト値は0（お気に入りではない）とします。
                database.execSQL("ALTER TABLE tags ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // postsテーブルに isDeletedFromSns カラムを追加
                database.execSQL("ALTER TABLE posts ADD COLUMN isDeletedFromSns INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // post_imagesテーブルを作成
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS `post_images` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `postId` TEXT NOT NULL,
                `imageUrl` TEXT NOT NULL,
                `orderIndex` INTEGER NOT NULL
            )
        """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // post_imagesテーブルにthumbnailUrlカラムを追加
                database.execSQL("ALTER TABLE post_images ADD COLUMN thumbnailUrl TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "logleaf_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
