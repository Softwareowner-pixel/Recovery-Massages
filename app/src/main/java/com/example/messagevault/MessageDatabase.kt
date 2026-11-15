package com.example.messagevault

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageRecord::class, MediaRecord::class], version = 5, exportSchema = false)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile private var INSTANCE: MessageDatabase? = null

        // v1 -> v2: remove duplicate rows, then add the unique index.
        // Existing saved messages are kept (no destructive reset).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM messages WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM messages GROUP BY packageName, sender, message, receivedAt)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_messages_packageName_sender_message_receivedAt` " +
                        "ON `messages` (`packageName`, `sender`, `message`, `receivedAt`)"
                )
            }
        }

        // v2 -> v3: new table for saved photos and videos. Messages are untouched.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sourceKey` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`receivedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_sourceKey` ON `media` (`sourceKey`)"
                )
            }
        }

        // v3 -> v4: WAMR-style features. "author" (who wrote it inside a group) and
        // "isDeleted" (sender deleted it for everyone). Existing messages are kept.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `author` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v4 -> v5: saved media remembers which chat it belongs to.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `media` ADD COLUMN `chatKey` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): MessageDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "message-vault.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
