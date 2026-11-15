package com.example.messagevault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Tab 1: all conversations, newest first. Search looks at chat names AND message text. */
@Composable
fun ChatsScreen(
    chats: List<ChatSummary>?,      // null = still loading
    query: String,
    onQueryChange: (String) -> Unit,
    accessEnabled: Boolean,
    onEnableAccess: () -> Unit,
    onOpenChat: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        if (!accessEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notification access is OFF", style = MaterialTheme.typography.titleMedium)
                    Text("Nothing can be saved until you allow it.")
                    Button(onClick = onEnableAccess) { Text("Turn on") }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search chats and messages") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )

        when {
            chats == null -> CenteredText("Loading...")
            chats.isEmpty() -> CenteredText(
                if (query.isBlank()) {
                    "No messages saved yet.\nNew messages from your chosen apps will appear here."
                } else {
                    "Nothing found."
                }
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(chats, key = { it.key }) { chat ->
                    ChatRow(chat = chat, onClick = { onOpenChat(chat) })
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChatAvatar(chat.packageName, chat.chat)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                chat.chat,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                previewText(chat.last),
                style = MaterialTheme.typography.bodyMedium,
                color = if (chat.last.isDeleted) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                chat.appName,
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatListTime(chat.last.receivedAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            if (chat.deletedCount > 0) {
                Text(
                    "🚫 ${chat.deletedCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error
                )
            }
        }
    }
}

@Composable
fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
