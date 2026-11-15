package com.example.messagevault

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** A simple PIN lock for opening the app. The PIN is stored only as a salted SHA-256 hash. */
object AppLock {
    private const val PREFS = "app_lock"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getString(KEY_HASH, null) != null

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, pin)).apply()
    }

    fun check(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_SALT, null) ?: return false
        return p.getString(KEY_HASH, null) == hash(salt, pin)
    }

    fun disable(context: Context) {
        prefs(context).edit().remove(KEY_HASH).remove(KEY_SALT).apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }
}
