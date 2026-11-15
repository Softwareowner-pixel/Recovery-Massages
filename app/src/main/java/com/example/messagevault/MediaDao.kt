package com.example.messagevault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: MediaRecord): Long

    @Query("SELECT COUNT(*) FROM media WHERE sourceKey = :key")
    suspend fun countByKey(key: String): Int

    @Query("SELECT * FROM media ORDER BY receivedAt DESC, id DESC")
    fun observeAll(): Flow<List<MediaRecord>>

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media")
    suspend fun clearAll()
}
