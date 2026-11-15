package com.example.messagevault

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The unique index stops the same message from being saved twice when an app
// re-posts / updates its notification (WhatsApp does this a lot).
@Entity(
    tableName = "messages",
    indices = [Index(value = ["packageName", "sender", "message", "receivedAt"], unique = true)]
)
data class MessageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    /** The chat: a contact name for 1:1 chats, the group name for groups. */
    val sender: String,
    val message: String,
    val receivedAt: Long,
    /** Who wrote it inside a group chat. Empty for 1:1 chats (and for messages saved by v1.2). */
    @ColumnInfo(defaultValue = "''") val author: String = "",
    /** True when the sender used "Delete for everyone" after we had already saved the message. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false
)
