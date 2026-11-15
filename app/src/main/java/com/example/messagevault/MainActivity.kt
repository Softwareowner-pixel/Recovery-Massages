package com.example.messagevault

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = MessageDatabase.get(this).messageDao()

        setContent {
            MaterialTheme {
                val records = dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("MessageVault") })
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "New notification messages are saved on this phone. Enable access below.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text("Open notification access settings")
                        }
                        Text("Saved messages: ${records.value.size}", style = MaterialTheme.typography.titleMedium)
                        if (records.value.isEmpty()) {
                            Text("No messages saved yet.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(records.value, key = { it.id }) { item ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${item.appName} • ${item.sender}", style = MaterialTheme.typography.titleSmall)
                                            Text(item.message.ifBlank { "(No preview text available)" })
                                            Text(
                                                DateFormat.getDateTimeInstance().format(Date(item.receivedAt)),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
