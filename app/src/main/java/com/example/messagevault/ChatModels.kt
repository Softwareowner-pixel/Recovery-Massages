package com.example.messagevault

import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** One conversation in the chat list (all saved messages of one chat in one app). */
data class ChatSummary(
    val key: String,
    val packageName: String,
    val appName: String,
    val chat: String,
    val last: MessageRecord,
    val count: Int,
    val deletedCount: Int
)

fun chatKey(record: MessageRecord): String = record.packageName + "\u0001" + record.sender

/** [messages] must be newest first (that is how the database returns them). */
fun buildChats(messages: List<MessageRecord>): List<ChatSummary> {
    val groups = LinkedHashMap<String, MutableList<MessageRecord>>()
    for (m in messages) groups.getOrPut(chatKey(m)) { ArrayList() }.add(m)
    return groups.map { (key, list) ->
        val first = list.first()
        ChatSummary(
            key = key,
            packageName = first.packageName,
            appName = first.appName,
            chat = first.sender.ifBlank { first.appName },
            last = first,
            count = list.size,
            deletedCount = list.count { it.isDeleted }
        )
    }
}

/** Text shown for a message in lists/previews. */
fun previewText(record: MessageRecord): String {
    val body = if (record.author.isNotBlank()) "${record.author}: ${record.message}" else record.message
    return if (record.isDeleted) "🚫 $body" else body
}

// ---- date / time helpers ----

private fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun startOfYesterday(): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = startOfDay(System.currentTimeMillis())
    c.add(Calendar.DAY_OF_YEAR, -1)
    return c.timeInMillis
}

fun dayKey(millis: Long): Long = startOfDay(millis)

fun formatClock(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/** Right-hand time in the chat list: clock today, "Yesterday", otherwise a short date. */
fun formatListTime(millis: Long): String {
    val day = startOfDay(millis)
    return when {
        day == startOfDay(System.currentTimeMillis()) -> formatClock(millis)
        day == startOfYesterday() -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))
    }
}

/** Day separator inside a chat. */
fun formatDayLabel(millis: Long): String {
    val day = startOfDay(millis)
    return when {
        day == startOfDay(System.currentTimeMillis()) -> "Today"
        day == startOfYesterday() -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

private val MEDIA_HINTS = listOf(
    "\uD83D\uDCF7", "\uD83C\uDFA5", "\uD83C\uDFA4", "\uD83C\uDFB5", "\uD83D\uDCC4", "\uD83D\uDDBC",
    "photo", "image", "video", "voice message", "audio", "document", "sticker", "gif"
)

/** True when a notification text looks like "Photo", "Voice message", "Document" ... */
fun looksLikeMedia(text: String): Boolean {
    val t = text.lowercase()
    return MEDIA_HINTS.any { t.contains(it) }
}
