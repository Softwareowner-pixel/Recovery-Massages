package com.example.messagevault

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessageNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Ignore notifications posted by this app itself.
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
        val messageText = bigText.ifBlank { text }

        // Save only notifications that contain visible text.
        if (title.isBlank() && messageText.isBlank()) return

        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        serviceScope.launch {
            MessageDatabase.get(applicationContext).messageDao().insert(
                MessageRecord(
                    packageName = sbn.packageName,
                    appName = appName,
                    sender = title,
                    message = messageText,
                    receivedAt = sbn.postTime
                )
            )
        }
    }
}
