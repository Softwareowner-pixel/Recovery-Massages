package com.example.messagevault

import android.content.Context

/** Small on/off settings that are not worth a database. */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_ALERT_ON_DELETE = "alert_on_delete"
    private const val KEY_ASKED_NOTIFICATION_PERMISSION = "asked_notification_permission"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Show a notification of our own when somebody deletes a message we had saved. */
    fun alertOnDelete(context: Context): Boolean = prefs(context).getBoolean(KEY_ALERT_ON_DELETE, true)

    fun setAlertOnDelete(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALERT_ON_DELETE, on).apply()
    }

    fun askedNotificationPermission(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASKED_NOTIFICATION_PERMISSION, false)

    fun setAskedNotificationPermission(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED_NOTIFICATION_PERMISSION, true).apply()
    }
}
