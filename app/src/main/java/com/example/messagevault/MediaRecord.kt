package com.example.messagevault

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A photo or video copied into MessageVault's private storage. */
@Entity(tableName = "media", indices = [Index(value = ["sourceKey"], unique = true)])
data class MediaRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,   // "i:<mediastore id>" or "v:<mediastore id>" - stops double copies
    val source: String,      // "WhatsApp" or "WhatsApp Business"
    val type: String,        // image, video, gif, sticker, voice, audio, document, status
    val mimeType: String,
    val fileName: String,    // file name inside MediaStorage.dir()
    val sizeBytes: Long,
    val receivedAt: Long,
    /** chatKey() of the chat this file most likely belongs to ("" = unknown, e.g. statuses). */
    @ColumnInfo(defaultValue = "''") val chatKey: String = ""
)
