package com.example.messagevault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // IGNORE = silently skip rows that already exist (see unique index on MessageRecord).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<MessageRecord>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: MessageRecord): Long

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC, id DESC")
    fun observeAll(): Flow<List<MessageRecord>>

    /** Messages of one chat saved at (almost) the given time. Used to find the deleted original. */
    @Query(
        "SELECT * FROM messages WHERE packageName = :pkg AND sender = :chat " +
            "AND receivedAt BETWEEN :fromTime AND :toTime"
    )
    suspend fun findAround(pkg: String, chat: String, fromTime: Long, toTime: Long): List<MessageRecord>

    /** Newest messages of an app in a time window. Used to guess which chat a media file belongs to. */
    @Query(
        "SELECT * FROM messages WHERE packageName = :pkg AND receivedAt BETWEEN :fromTime AND :toTime " +
            "ORDER BY receivedAt DESC LIMIT :limit"
    )
    suspend fun latestBetween(pkg: String, fromTime: Long, toTime: Long, limit: Int): List<MessageRecord>

    /** Everything, oldest first (for export). */
    @Query("SELECT * FROM messages ORDER BY receivedAt ASC, id ASC")
    suspend fun getAll(): List<MessageRecord>

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE packageName = :pkg AND sender = :chat")
    suspend fun deleteChat(pkg: String, chat: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
