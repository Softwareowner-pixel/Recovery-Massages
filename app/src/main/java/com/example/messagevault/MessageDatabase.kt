package com.example.messagevault

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageRecord::class], version = 1, exportSchema = false)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: MessageDatabase? = null

        fun get(context: Context): MessageDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "message-vault.db"
                ).build().also { INSTANCE = it }
            }
    }
}
