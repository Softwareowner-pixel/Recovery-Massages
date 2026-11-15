package com.example.messagevault

import android.app.Notification
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/** Turns one Android notification into zero or more messages worth saving. */
object NotificationParser {

    /**
     * chat   = the conversation (contact or group name)
     * author = who wrote it inside a group (empty in 1:1 chats)
     */
    data class Parsed(
        val chat: String,
        val author: String,
        val text: String,
        val time: Long,
        val kind: DeletedMessages.Kind
    )

    private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

    fun parse(notification: Notification, postTime: Long): List<Parsed> {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

        // Chat apps (WhatsApp, Telegram, ...) use MessagingStyle. It carries the individual
        // messages, so we do not end up saving useless summaries like "3 new messages".
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null) {
            val chat = style.conversationTitle?.toString()?.trim().orEmpty().ifBlank { title }
            val items = style.messages.mapNotNull { m ->
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) return@mapNotNull null
                val who = m.person?.name?.toString()?.trim().orEmpty()
                // In a 1:1 chat the person is the chat itself, so there is no separate author.
                val author = if (who.isNotBlank() && who != chat) who else ""
                Parsed(
                    chat = chat,
                    author = author,
                    text = text,
                    time = if (m.timestamp > 0) m.timestamp else postTime,
                    kind = DeletedMessages.classify(text)
                )
            }
            if (items.isNotEmpty()) return items
        }

        // Normal (non-chat-style) notification.
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = bigText.ifBlank { text }
        // Title-only notifications ("Downloading...", "Checking for messages") are noise.
        if (body.isBlank()) return emptyList()

        // Notification.`when` stays the same when an app updates a notification in place,
        // which lets the unique index drop the repeats. Ignore it if it is clearly bogus.
        val w = notification.`when`
        val time = if (w > 0 && abs(postTime - w) < ONE_DAY_MS) w else postTime
        return listOf(
            Parsed(
                chat = title,
                author = "",
                text = body,
                time = time,
                kind = DeletedMessages.classify(body)
            )
        )
    }
}
