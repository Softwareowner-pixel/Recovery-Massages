package com.example.messagevault

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MessageNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Notifications are handled one after another so "message + its deletion" never race.
    private val dbLock = Mutex()

    // Many "something changed" signals -> at most one pending media scan (CONFLATED).
    private val scanRequests = Channel<Unit>(Channel.CONFLATED)
    private var mediaObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            while (true) {
                scanRequests.receive()
                delay(MEDIA_SETTLE_DELAY_MS) // let WhatsApp finish writing the file
                try {
                    MediaCapture.scan(applicationContext)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Media scan failed", t)
                }
                try {
                    FolderCapture.scan(applicationContext) // statuses + documents
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Folder scan failed", t)
                }
            }
        }
        // Statuses and documents do not trigger any signal, so look again every couple of minutes.
        serviceScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                requestMediaScan()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        registerMediaObserver()
        requestMediaScan() // catch up on anything that arrived while we were not running
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (t: Throwable) {
            // A badly-behaved app must never be able to crash our service.
            Log.w(TAG, "Could not process notification from ${sbn.packageName}", t)
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return                    // ignore our own notifications
        if (pkg !in MonitoredApps.get(applicationContext)) return  // only apps the user chose

        requestMediaScan() // a photo/video notification usually means a new file is arriving

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return // "5 new messages" bundles
        if (sbn.isOngoing) return                         // music players, downloads, VPN ...

        val parsed = NotificationParser.parse(notification, sbn.postTime)
        if (parsed.isEmpty()) return

        serviceScope.launch {
            try {
                Avatars.saveFrom(applicationContext, notification, pkg, parsed.first().chat)
            } catch (t: Throwable) {
                Log.w(TAG, "Could not save chat picture", t)
            }
            try {
                dbLock.withLock { save(pkg, parsed) }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not save notification from $pkg", t)
            }
        }
    }

    private suspend fun save(pkg: String, parsed: List<NotificationParser.Parsed>) {
        val dao = MessageDatabase.get(applicationContext).messageDao()
        val appName = resolveAppName(pkg)

        // 1) save the normal messages first, so a deletion in the same update can find its original
        val normal = parsed
            .filter { it.kind == DeletedMessages.Kind.NORMAL }
            .map {
                MessageRecord(
                    packageName = pkg,
                    appName = appName,
                    sender = it.chat,
                    message = it.text,
                    receivedAt = it.time,
                    author = it.author
                )
            }
        if (normal.isNotEmpty()) dao.insertAll(normal)

        // 2) "This message was deleted" -> flag the message we saved earlier
        // (DELETED_BY_ME - you deleting your own message - is ignored)
        parsed.filter { it.kind == DeletedMessages.Kind.DELETED_BY_SENDER }
            .forEach { applyDeletion(dao, pkg, appName, it) }
    }

    private suspend fun applyDeletion(
        dao: MessageDao,
        pkg: String,
        appName: String,
        p: NotificationParser.Parsed
    ) {
        // The "deleted" placeholder keeps the time of the original message, so the original is
        // the saved message of this chat with (almost) the same time.
        val candidates = dao.findAround(pkg, p.chat, p.time - TIME_TOLERANCE_MS, p.time + TIME_TOLERANCE_MS)
        fun sameAuthor(r: MessageRecord) = p.author.isBlank() || r.author.isBlank() || r.author == p.author
        val matching = candidates.filter { sameAuthor(it) }

        // Already flagged earlier (WhatsApp re-posts the same notification again and again).
        if (matching.any { it.isDeleted }) return

        val original = matching.firstOrNull()
        if (original != null) {
            dao.markDeleted(original.id)
            showDeletedAlert(appName, p.chat, original)
        } else if (candidates.isEmpty()) {
            // We never saw the original (app was off / notification muted). Keep a visible trace.
            dao.insert(
                MessageRecord(
                    packageName = pkg,
                    appName = appName,
                    sender = p.chat,
                    message = p.text,
                    receivedAt = p.time,
                    author = p.author,
                    isDeleted = true
                )
            )
        }
    }

    private fun showDeletedAlert(appName: String, chat: String, original: MessageRecord) {
        if (!AppSettings.alertOnDelete(applicationContext)) return
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, "Deleted message alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val who = original.author.ifBlank { chat }.ifBlank { appName }
            val open = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\uD83D\uDEAB $who deleted a message")
                .setContentText(original.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(original.message))
                .setSubText(appName)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(original.id.toInt(), notification)
        } catch (t: Throwable) {
            // No permission / no notification service: the message is still flagged in the app.
            Log.w(TAG, "Could not show deleted-message alert", t)
        }
    }

    // Only asks for a scan when the user has turned "Save photos and videos" on.
    private fun requestMediaScan() {
        if (MediaCapture.isEnabled(applicationContext) || FolderCapture.isEnabled(applicationContext)) {
            scanRequests.trySend(Unit)
        }
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                requestMediaScan()
            }
        }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        mediaObserver = observer
    }

    private fun unregisterMediaObserver() {
        mediaObserver?.let { contentResolver.unregisterContentObserver(it) }
        mediaObserver = null
    }

    @Suppress("DEPRECATION")
    private fun resolveAppName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    // Android can drop the connection to a listener (battery savers, updates ...).
    // Ask it to reconnect so capture does not silently stop.
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unregisterMediaObserver()
        try {
            NotificationListenerService.requestRebind(
                ComponentName(this, MessageNotificationListener::class.java)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "requestRebind failed", t)
        }
    }

    override fun onDestroy() {
        unregisterMediaObserver()
        serviceScope.cancel() // the old code leaked this scope
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MessageVault"
        const val MEDIA_SETTLE_DELAY_MS = 2500L
        const val TIME_TOLERANCE_MS = 2000L
        const val POLL_INTERVAL_MS = 120_000L
        const val ALERT_CHANNEL = "deleted_alerts"
    }
}
