package com.example.messagevault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Tab 2: only the messages that the sender deleted for everyone. */
@Composable
fun DeletedScreen(
    deleted: List<MessageRecord>?,   // null = still loading, newest first
    onOpen: (MessageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        deleted == null -> CenteredText("Loading...", modifier)
        deleted.isEmpty() -> CenteredText(
            "No deleted messages yet.\nWhen someone uses \"Delete for everyone\", the original text " +
                "is kept here (only if the app had already saved it).",
            modifier
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deleted, key = { it.id }) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(record) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val who = if (record.author.isNotBlank()) "${record.author} • ${record.sender}" else record.sender
                        Text(who.ifBlank { record.appName }, style = MaterialTheme.typography.titleSmall)
                        Text(record.message)
                        Text(
                            "${record.appName} • ${formatDateTime(record.receivedAt)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
