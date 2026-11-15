package com.example.messagevault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private sealed interface ChatItem {
    val id: String
    data class Day(val label: String, val day: Long) : ChatItem {
        override val id: String get() = "day-$day"
    }
    data class Msg(val record: MessageRecord) : ChatItem {
        override val id: String get() = "msg-${record.id}"
    }
    data class Media(val record: MediaRecord) : ChatItem {
        override val id: String get() = "media-${record.id}"
    }
}

/** A single conversation shown as chat bubbles, oldest at the top. Deleted messages are red. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    appName: String,
    messages: List<MessageRecord>,    // oldest first
    media: List<MediaRecord>,         // saved files that most likely belong to this chat
    onOpenMedia: (MediaRecord) -> Unit,
    onBack: () -> Unit,
    onDeleteMessage: (MessageRecord) -> Unit,
    onDeleteChat: () -> Unit
) {
    BackHandler(onBack = onBack)
    var selected by remember { mutableStateOf<MessageRecord?>(null) }
    var confirmDeleteChat by remember { mutableStateOf(false) }

    val chatItems = remember(messages, media) {
        // Messages and saved media in one timeline.
        val entries = ArrayList<Pair<Long, ChatItem>>()
        for (m in messages) entries.add(Pair(m.receivedAt, ChatItem.Msg(m)))
        for (f in media) entries.add(Pair(f.receivedAt, ChatItem.Media(f)))
        entries.sortBy { it.first }

        val out = ArrayList<ChatItem>()
        var lastDay = -1L
        for ((time, item) in entries) {
            val d = dayKey(time)
            if (d != lastDay) {
                out.add(ChatItem.Day(formatDayLabel(time), d))
                lastDay = d
            }
            out.add(item)
        }
        out
    }

    val listState = rememberLazyListState()
    // Jump to the newest message when the chat opens and whenever a new one arrives.
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) listState.scrollToItem(chatItems.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        Text(appName, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { confirmDeleteChat = true }) { Text("Delete chat") } }
            )
        }
    ) { padding ->
        if (chatItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) { CenteredText("No messages.") }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chatItems, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.Day -> DayChip(item.label)
                        is ChatItem.Msg -> Bubble(item.record, onLongPress = { selected = item.record })
                        is ChatItem.Media -> Row(Modifier.fillMaxWidth()) {
                            MediaThumb(
                                record = item.record,
                                modifier = Modifier.width(180.dp),
                                onClick = { onOpenMedia(item.record) }
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { record ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Message") },
            text = { Text(record.message) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(record.message))
                        selected = null
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        selected = null
                        onDeleteMessage(record)
                    }) { Text("Delete") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }

    if (confirmDeleteChat) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChat = false },
            title = { Text("Delete this chat?") },
            text = { Text("All saved messages of \"$title\" will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteChat = false
                    onDeleteChat()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteChat = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(record: MessageRecord, onLongPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val deleted = record.isDeleted
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (deleted) colors.errorContainer else colors.primaryContainer)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (record.author.isNotBlank()) {
                Text(
                    record.author,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
            if (deleted) {
                Text(
                    "🚫 Deleted by sender",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error
                )
            }
            Text(record.message, color = if (deleted) colors.onErrorContainer else colors.onPrimaryContainer)
            Text(
                formatClock(record.receivedAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
