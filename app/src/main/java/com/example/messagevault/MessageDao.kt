package com.example.messagevault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(record: MessageRecord)

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<MessageRecord>>
}
