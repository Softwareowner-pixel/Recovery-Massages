package com.example.messagevault

import android.content.Context

/**
 * Which apps MessageVault is allowed to save notifications from.
 * (The README always promised "apps the user chooses", but the old code saved
 * notifications from EVERY app, including OTP / banking ones.)
 */
object MonitoredApps {
    private const val PREFS = "monitored_apps"
    private const val KEY_PACKAGES = "packages"

    // Used until the user picks their own list.
    val DEFAULTS: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms", // Signal
        "com.facebook.orca",          // Messenger
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    fun get(context: Context): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Copy: the set returned by SharedPreferences must never be modified.
        return prefs.getStringSet(KEY_PACKAGES, null)?.toSet() ?: DEFAULTS
    }

    fun set(context: Context, packages: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, HashSet(packages))
            .apply()
    }
}
