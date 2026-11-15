package com.example.messagevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile

/**
 * Saves WhatsApp **statuses** and **documents**. Android does not show these in the gallery, so the
 * user picks WhatsApp's media folder once (Android > media > com.whatsapp) and grants access to it.
 * Only files that appear after access was granted are saved. A status is only on the phone after you
 * have opened (viewed) it in WhatsApp.
 */
object FolderCapture {
    private const val TAG = "MessageVault"
    private const val PREFS = "folder_capture"
    private const val KEY_URI = "tree_uri"
    private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    private const val MAX_DEPTH = 6

    /** Where the folder picker should start (Android 8+ only uses it as a hint). */
    val PICKER_START: Uri = Uri.parse(
        "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fmedia%2Fcom.whatsapp"
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun treeUri(context: Context): Uri? = prefs(context).getString(KEY_URI, null)?.let { Uri.parse(it) }

    fun isEnabled(context: Context): Boolean = treeUri(context) != null

    fun grant(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not keep folder permission", t)
        }
        prefs(context).edit()
            .putString(KEY_URI, uri.toString())
            .putLong(KEY_LAST_SCAN_MS, System.currentTimeMillis()) // only NEW files from now on
            .apply()
    }

    fun revoke(context: Context) {
        treeUri(context)?.let {
            try {
                context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {
            }
        }
        prefs(context).edit().remove(KEY_URI).apply()
    }

    suspend fun scan(context: Context) {
        val app = context.applicationContext
        val uri = treeUri(app) ?: return
        val root = DocumentFile.fromTreeUri(app, uri) ?: return
        if (!root.canRead()) return

        val startMs = System.currentTimeMillis()
        val sinceMs = prefs(app).getLong(KEY_LAST_SCAN_MS, startMs) - 15_000L
        val dao = MessageDatabase.get(app).mediaDao()
        walk(app, dao, root, 0, "", sinceMs)
        prefs(app).edit().putLong(KEY_LAST_SCAN_MS, startMs).apply()
    }

    // kind: "" = not inside a status/documents folder yet, otherwise "status" or "document".
    private suspend fun walk(context: Context, dao: MediaDao, dir: DocumentFile, depth: Int, kind: String, sinceMs: Long) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (name.equals("Sent", ignoreCase = true) || name.equals("Private", ignoreCase = true)) continue
                val childKind = when {
                    name == ".Statuses" -> "status"
                    name.contains("Documents", ignoreCase = true) -> "document"
                    else -> kind
                }
                // Do not wander into the big photo/video folders: only follow the way to status/documents.
                val worthEntering = kind.isNotEmpty() || childKind.isNotEmpty() ||
                    name.startsWith("com.whatsapp") || name == "WhatsApp" ||
                    name == "WhatsApp Business" || name == "Media"
                if (worthEntering) walk(context, dao, child, depth + 1, childKind, sinceMs)
            } else if (kind.isNotEmpty()) {
                capture(context, dao, child, name, kind, sinceMs)
            }
        }
    }

    private suspend fun capture(context: Context, dao: MediaDao, file: DocumentFile, name: String, kind: String, sinceMs: Long) {
        if (name.startsWith(".nomedia")) return
        val modified = file.lastModified()
        if (modified < sinceMs) return
        val size = file.length()
        if (size <= 0L || size > MediaCapture.MAX_FILE_BYTES) return

        val key = "f:$kind:$name:$modified"
        if (dao.countByKey(key) > 0) return

        val source = if (file.uri.toString().contains("w4b")) "WhatsApp Business" else "WhatsApp"
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = file.type
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "${kind}_${modified}_$safeName"

        if (MediaCapture.copy(context, file.uri, MediaStorage.file(context, fileName))) {
            dao.insert(
                MediaRecord(
                    sourceKey = key,
                    source = source,
                    type = kind,
                    mimeType = mime,
                    fileName = fileName,
                    sizeBytes = size,
                    receivedAt = modified,
                    chatKey = if (kind == "document") MediaCapture.guessChatKey(context, source, modified) else ""
                )
            )
        }
    }
}
