package com.example.messagevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Tab 4: permissions, apps to monitor, alerts, media saving and cleanup. */
@Composable
fun SettingsScreen(
    accessEnabled: Boolean,
    monitoredCount: Int,
    alertOnDelete: Boolean,
    mediaEnabled: Boolean,
    mediaPermitted: Boolean,
    messageCount: Int,
    mediaCount: Int,
    onOpenAccessSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onChooseApps: () -> Unit,
    onToggleAlert: (Boolean) -> Unit,
    onToggleMedia: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onClearMessages: () -> Unit,
    onClearMedia: () -> Unit,
    folderEnabled: Boolean,
    onGrantFolder: () -> Unit,
    onRevokeFolder: () -> Unit,
    lockEnabled: Boolean,
    onSetPin: (String) -> Unit,
    onDisableLock: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirm by rememberSaveable { mutableStateOf(0) } // 0 = none, 1 = messages, 2 = media
    var showPinDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(
            accessEnabled = accessEnabled,
            monitoredCount = monitoredCount,
            onOpenAccessSettings = onOpenAccessSettings,
            onOpenAppInfo = onOpenAppInfo
        )

        Button(onClick = onChooseApps, modifier = Modifier.fillMaxWidth()) {
            Text("Choose apps to monitor ($monitoredCount selected)")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Deleted message alerts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Shows a notification with the original text when someone deletes a message " +
                            "that was already saved.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = alertOnDelete, onCheckedChange = onToggleAlert)
            }
        }

        MediaSettingsCard(enabled = mediaEnabled, permitted = mediaPermitted, onToggle = onToggleMedia)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Statuses and documents", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android hides these from the gallery. Give MessageVault access to WhatsApp's folder once: " +
                        "tap the button, open Android > media > com.whatsapp, then tap \"Use this folder\" and " +
                        "Allow. Only new files are saved. A status is on the phone only after you have viewed it.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (folderEnabled) {
                    Text("Folder access: ON", color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onGrantFolder) { Text("Change folder") }
                        OutlinedButton(onClick = onRevokeFolder) { Text("Turn off") }
                    }
                } else {
                    Button(onClick = onGrantFolder) { Text("Give folder access") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App lock (PIN)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ask for a PIN when the app is opened. Also hides the app in the recent-apps screen " +
                            "and blocks screenshots.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { if (it) showPinDialog = true else onDisableLock() }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep it running", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Some phones (Xiaomi, Oppo, Vivo, Infinix, Tecno, Samsung ...) stop background apps. " +
                        "Set MessageVault to \"Unrestricted\" / \"No restrictions\" in battery settings and " +
                        "allow Auto-start, otherwise messages can be missed.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onOpenBatterySettings) { Text("Battery optimisation settings") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Storage", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onExport, enabled = messageCount > 0) {
                    Text("Export chats as text file")
                }
                OutlinedButton(onClick = { confirm = 1 }, enabled = messageCount > 0) {
                    Text("Delete all messages ($messageCount)")
                }
                OutlinedButton(onClick = { confirm = 2 }, enabled = mediaCount > 0) {
                    Text("Delete all media ($mediaCount)")
                }
            }
        }

        Text(
            "Messages are saved only from the notifications your apps show. Hidden previews, muted chats " +
                "and view-once media cannot be captured. Everything stays on this phone, unencrypted, " +
                "in the app's private storage. Use only on your own phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showPinDialog) {
        var pin1 by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        val valid = pin1.length >= 4 && pin1 == pin2
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set a PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { pin1 = it.filter { c -> c.isDigit() }.take(12) },
                        singleLine = true,
                        label = { Text("New PIN (4-12 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { pin2 = it.filter { c -> c.isDigit() }.take(12) },
                        singleLine = true,
                        label = { Text("Repeat PIN") },
                        isError = pin2.isNotEmpty() && pin1 != pin2,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    Text("If you forget the PIN you must clear the app's data (saved messages are lost).",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    showPinDialog = false
                    onSetPin(pin1)
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }

    if (confirm != 0) {
        AlertDialog(
            onDismissRequest = { confirm = 0 },
            title = { Text(if (confirm == 1) "Delete all messages?" else "Delete all media?") },
            text = { Text("Everything saved here will be permanently removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    val which = confirm
                    confirm = 0
                    if (which == 1) onClearMessages() else onClearMedia()
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirm = 0 }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatusCard(
    accessEnabled: Boolean,
    monitoredCount: Int,
    onOpenAccessSettings: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (accessEnabled) colors.secondaryContainer else colors.errorContainer,
            contentColor = if (accessEnabled) colors.onSecondaryContainer else colors.onErrorContainer
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (accessEnabled) "Notification access: ON" else "Notification access: OFF",
                style = MaterialTheme.typography.titleMedium
            )
            if (accessEnabled) {
                Text("New messages from $monitoredCount selected app(s) are saved on this phone.")
                TextButton(onClick = onOpenAccessSettings) { Text("Notification access settings") }
            } else {
                Text("Nothing is saved until you turn on notification access for MessageVault.")
                Button(onClick = onOpenAccessSettings) { Text("Open notification access settings") }
                Text(
                    "If Android blocks it with \"Restricted setting\" (common for apps installed from an APK): " +
                        "open App info, tap the three-dot menu, choose \"Allow restricted settings\", then try again.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onOpenAppInfo) { Text("Open App info") }
            }
        }
    }
}
