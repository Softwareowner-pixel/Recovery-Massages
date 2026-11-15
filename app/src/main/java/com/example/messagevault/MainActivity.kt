package com.example.messagevault

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Whether the user has switched on notification access for this app.
    private val accessEnabled = mutableStateOf(false)
    // "Save media" switch + whether Android has granted the media permission.
    private val mediaEnabled = mutableStateOf(false)
    private val mediaPermitted = mutableStateOf(false)
    private val alertOnDelete = mutableStateOf(true)
    private val folderEnabled = mutableStateOf(false)
    private val lockEnabled = mutableStateOf(false)
    private val unlocked = mutableStateOf(false)
    private var lastStopMs = 0L

    private val db by lazy { MessageDatabase.get(this) }
    private val dao by lazy { db.messageDao() }
    private val mediaDao by lazy { db.mediaDao() }
    // Created ONCE (not inside setContent, which would resubscribe on every recomposition).
    private val messagesFlow by lazy { dao.observeAll() }
    private val mediaFlow by lazy { mediaDao.observeAll() }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val granted = MediaCapture.hasPermission(this)
        mediaPermitted.value = granted
        if (granted) {
            MediaCapture.setEnabled(this, true)
            mediaEnabled.value = true
        } else {
            Toast.makeText(
                this,
                "Allow access to ALL photos, videos and audio to save media.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Android 13+: needed so the "message deleted" alert can be shown.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // The user picks WhatsApp's media folder once (for statuses and documents).
    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            FolderCapture.grant(this, uri)
            folderEnabled.value = true
            Toast.makeText(this, "Folder access granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) lifecycleScope.launch(Dispatchers.IO) { exportTo(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermissionOnce()

        lockEnabled.value = AppLock.isEnabled(this)
        unlocked.value = !lockEnabled.value
        applySecureFlag()

        setContent {
            MessageVaultTheme {
                if (unlocked.value) {
                    AppContent()
                } else {
                    LockScreen(onSubmit = { pin ->
                        val ok = AppLock.check(this@MainActivity, pin)
                        if (ok) unlocked.value = true
                        ok
                    })
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppContent() {
                // null = still loading (so "No messages" is not flashed while the DB opens)
                val messages: List<MessageRecord>? by messagesFlow
                    .collectAsStateWithLifecycle(initialValue = null)
                val media: List<MediaRecord>? by mediaFlow
                    .collectAsStateWithLifecycle(initialValue = null)

                var tab by rememberSaveable { mutableStateOf(0) } // 0 chats, 1 deleted, 2 media, 3 settings
                var showPicker by rememberSaveable { mutableStateOf(false) }
                var openChatKey by rememberSaveable { mutableStateOf<String?>(null) }
                var query by rememberSaveable { mutableStateOf("") }
                var monitored by remember { mutableStateOf(MonitoredApps.get(applicationContext)) }
                var selectedMedia by remember { mutableStateOf<MediaRecord?>(null) }

                val allMessages: List<MessageRecord>? = messages
                val allMedia: List<MediaRecord>? = media

                val chats = remember(allMessages) { allMessages?.let { buildChats(it) } }
                val deletedMessages = remember(allMessages) { allMessages?.filter { it.isDeleted } }

                // Chat list after the search box (chat name OR any message text).
                val visibleChats = remember(chats, allMessages, query) {
                    val q = query.trim().lowercase()
                    if (chats == null || q.isEmpty() || allMessages == null) {
                        chats
                    } else {
                        val keysWithText = allMessages
                            .filter { it.message.lowercase().contains(q) }
                            .map { chatKey(it) }
                            .toHashSet()
                        chats.filter { it.chat.lowercase().contains(q) || it.key in keysWithText }
                    }
                }

                val chatKeyOpen = openChatKey
                if (chatKeyOpen != null) {
                    val chatMessages = remember(allMessages, chatKeyOpen) {
                        (allMessages ?: emptyList())
                            .filter { chatKey(it) == chatKeyOpen }
                            .sortedWith(compareBy<MessageRecord>({ it.receivedAt }, { it.id }))
                    }
                    val first = chatMessages.firstOrNull()
                    val chatMedia = remember(allMedia, chatKeyOpen) {
                        (allMedia ?: emptyList()).filter { it.chatKey == chatKeyOpen }
                    }
                    ChatScreen(
                        title = if (first == null) "Chat" else first.sender.ifBlank { first.appName },
                        appName = first?.appName ?: "",
                        messages = chatMessages,
                        media = chatMedia,
                        onOpenMedia = { selectedMedia = it },
                        onBack = { openChatKey = null },
                        onDeleteMessage = { record -> lifecycleScope.launch { dao.deleteById(record.id) } },
                        onDeleteChat = {
                            val target = first
                            openChatKey = null
                            if (target != null) {
                                lifecycleScope.launch { dao.deleteChat(target.packageName, target.sender) }
                            }
                        }
                    )
                } else if (showPicker) {
                    AppPickerScreen(
                        selected = monitored,
                        onToggle = { pkg, enabled ->
                            val updated = if (enabled) monitored + pkg else monitored - pkg
                            monitored = updated
                            MonitoredApps.set(applicationContext, updated)
                        },
                        onBack = { showPicker = false }
                    )
                } else {
                    // Back from another tab goes to the chat list first.
                    BackHandler(enabled = tab != 0) { tab = 0 }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        when (tab) {
                                            0 -> "MessageVault"
                                            1 -> "Deleted messages"
                                            2 -> "Media"
                                            else -> "Settings"
                                        }
                                    )
                                }
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tab == 0,
                                    onClick = { tab = 0 },
                                    icon = { Text("💬") },
                                    label = { Text("Chats") }
                                )
                                NavigationBarItem(
                                    selected = tab == 1,
                                    onClick = { tab = 1 },
                                    icon = {
                                        val n = deletedMessages?.size ?: 0
                                        if (n > 0) {
                                            BadgedBox(badge = { Badge { Text(if (n > 99) "99+" else n.toString()) } }) {
                                                Text("🚫")
                                            }
                                        } else {
                                            Text("🚫")
                                        }
                                    },
                                    label = { Text("Deleted") }
                                )
                                NavigationBarItem(
                                    selected = tab == 2,
                                    onClick = { tab = 2 },
                                    icon = { Text("🖼") },
                                    label = { Text("Media") }
                                )
                                NavigationBarItem(
                                    selected = tab == 3,
                                    onClick = { tab = 3 },
                                    icon = { Text("⚙") },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    ) { padding ->
                        val content = Modifier.padding(padding)
                        when (tab) {
                            0 -> ChatsScreen(
                                chats = visibleChats,
                                query = query,
                                onQueryChange = { query = it },
                                accessEnabled = accessEnabled.value,
                                onEnableAccess = ::openNotificationAccessSettings,
                                onOpenChat = { openChatKey = it.key },
                                modifier = content
                            )
                            1 -> DeletedScreen(
                                deleted = deletedMessages,
                                onOpen = { record -> openChatKey = chatKey(record) },
                                modifier = content
                            )
                            2 -> MediaScreen(
                                media = allMedia,
                                mediaEnabled = mediaEnabled.value,
                                onOpen = { selectedMedia = it },
                                modifier = content
                            )
                            else -> SettingsScreen(
                                accessEnabled = accessEnabled.value,
                                monitoredCount = monitored.size,
                                alertOnDelete = alertOnDelete.value,
                                mediaEnabled = mediaEnabled.value,
                                mediaPermitted = mediaPermitted.value,
                                messageCount = allMessages?.size ?: 0,
                                mediaCount = allMedia?.size ?: 0,
                                onOpenAccessSettings = ::openNotificationAccessSettings,
                                onOpenAppInfo = ::openAppInfo,
                                onChooseApps = { showPicker = true },
                                onToggleAlert = ::toggleAlert,
                                onToggleMedia = ::toggleMediaSaving,
                                onOpenBatterySettings = ::openBatterySettings,
                                onClearMessages = { lifecycleScope.launch { dao.clearAll() } },
                                onClearMedia = {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        mediaDao.clearAll()
                                        MediaStorage.deleteAll(applicationContext)
                                    }
                                },
                                folderEnabled = folderEnabled.value,
                                onGrantFolder = {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Open Android > media > com.whatsapp, then tap \"Use this folder\".",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    folderLauncher.launch(FolderCapture.PICKER_START)
                                },
                                onRevokeFolder = {
                                    FolderCapture.revoke(this@MainActivity)
                                    folderEnabled.value = false
                                },
                                lockEnabled = lockEnabled.value,
                                onSetPin = ::setPin,
                                onDisableLock = ::disableLock,
                                onExport = { exportLauncher.launch("MessageVault-export.txt") },
                                modifier = content
                            )
                        }
                    }
                }

                selectedMedia?.let { item ->
                    MediaDialog(
                        item = item,
                        onOpenExternally = { openMedia(item) },
                        onDelete = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                mediaDao.deleteById(item.id)
                                MediaStorage.delete(applicationContext, item.fileName)
                            }
                        },
                        onDismiss = { selectedMedia = null }
                    )
                }
    }

    // The user comes back from Android's settings / permission screens -> refresh the status.
    override fun onResume() {
        super.onResume()
        accessEnabled.value = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        mediaEnabled.value = MediaCapture.isEnabled(this)
        mediaPermitted.value = MediaCapture.hasPermission(this)
        alertOnDelete.value = AppSettings.alertOnDelete(this)
        folderEnabled.value = FolderCapture.isEnabled(this)
        lockEnabled.value = AppLock.isEnabled(this)
    }

    // Lock again when the app was left for more than 30 seconds (short trips to Android's
    // settings screens do not ask for the PIN again).
    override fun onStop() {
        super.onStop()
        lastStopMs = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (lockEnabled.value && unlocked.value && lastStopMs > 0L &&
            SystemClock.elapsedRealtime() - lastStopMs > 30_000L
        ) {
            unlocked.value = false
        }
    }

    private fun applySecureFlag() {
        if (lockEnabled.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun setPin(pin: String) {
        AppLock.setPin(this, pin)
        lockEnabled.value = true
        applySecureFlag()
        Toast.makeText(this, "PIN lock is ON.", Toast.LENGTH_SHORT).show()
    }

    private fun disableLock() {
        AppLock.disable(this)
        lockEnabled.value = false
        applySecureFlag()
    }

    // Writes every saved message into a plain text file the user chose.
    private suspend fun exportTo(uri: Uri) {
        val all = dao.getAll()
        val byChat = LinkedHashMap<String, MutableList<MessageRecord>>()
        for (m in all) byChat.getOrPut(chatKey(m)) { ArrayList() }.add(m)

        val text = StringBuilder()
        for ((_, list) in byChat) {
            val first = list.first()
            text.append("=== ").append(first.sender.ifBlank { first.appName })
                .append(" (").append(first.appName).append(") ===\n")
            for (m in list) {
                text.append('[').append(formatDateTime(m.receivedAt)).append("] ")
                if (m.author.isNotBlank()) text.append(m.author).append(": ")
                text.append(m.message)
                if (m.isDeleted) text.append("  [DELETED BY SENDER]")
                text.append('\n')
            }
            text.append('\n')
        }
        val message = try {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toString().toByteArray()) }
            "Exported ${all.size} messages."
        } catch (t: Throwable) {
            "Export failed."
        }
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (AppSettings.askedNotificationPermission(this)) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        AppSettings.setAskedNotificationPermission(this)
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun toggleAlert(on: Boolean) {
        AppSettings.setAlertOnDelete(this, on)
        alertOnDelete.value = on
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toggleMediaSaving(on: Boolean) {
        when {
            !on -> {
                MediaCapture.setEnabled(this, false)
                mediaEnabled.value = false
            }
            MediaCapture.hasPermission(this) -> {
                MediaCapture.setEnabled(this, true)
                mediaEnabled.value = true
                mediaPermitted.value = true
            }
            else -> mediaPermissionLauncher.launch(MediaCapture.requiredPermissions())
        }
    }

    private fun openMedia(record: MediaRecord) {
        val file = MediaStorage.file(this, record.fileName)
        if (!file.exists()) {
            Toast.makeText(this, "This file is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, record.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app found to open this file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            // A few phone makers remove this screen; fall back to the main settings.
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            openAppInfo()
        }
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}
