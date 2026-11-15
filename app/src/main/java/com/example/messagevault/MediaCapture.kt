package com.example.messagevault

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Copies NEW WhatsApp photos/videos/voice notes (files WhatsApp itself already saved in shared storage and
 * that show up in MediaStore / the gallery) into MessageVault's private folder.
 *
 * It does not touch WhatsApp's private data and cannot get media that WhatsApp has not
 * downloaded. Old media is never imported: only files added after the user turns this on.
 */
object MediaCapture {
    private enum class Kind { IMAGE, VIDEO, AUDIO }

    private const val TAG = "MessageVault"
    private const val PREFS = "media_capture"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SCAN_SEC = "last_scan_sec"

    const val MAX_FILE_BYTES = 200L * 1024 * 1024 // bigger files are skipped to protect storage

    // Folders WhatsApp / WhatsApp Business store received media in (SQL LIKE patterns).
    private val SOURCE_PATHS = listOf(
        "%/Android/media/com.whatsapp/%",
        "%/Android/media/com.whatsapp.w4b/%",
        "%/WhatsApp/Media/%",          // older Android versions
        "%/WhatsApp Business/Media/%"
    )

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_ENABLED, enabled)
        // Start counting from "now" so old gallery media is NOT imported.
        if (enabled) editor.putLong(KEY_LAST_SCAN_SEC, System.currentTimeMillis() / 1000)
        editor.apply()
    }

    /**
     * Which chat does a file belong to? Files carry no chat name, so we look at the messages saved
     * shortly before the file appeared (WhatsApp notifies "Photo" / "Voice message" first) and take the
     * newest one that looks like a media message. It is a good guess, not a guarantee.
     */
    suspend fun guessChatKey(context: Context, source: String, addedMs: Long): String {
        val pkg = if (source == "WhatsApp Business") "com.whatsapp.w4b" else "com.whatsapp"
        val recent = MessageDatabase.get(context).messageDao()
            .latestBetween(pkg, addedMs - 180_000L, addedMs + 60_000L, 8)
        if (recent.isEmpty()) return ""
        val hint = recent.firstOrNull { looksLikeMedia(it.message) } ?: recent.first()
        return chatKey(hint)
    }

    /** Looks for new WhatsApp media since the last scan and copies it. Call from a background thread. */
    suspend fun scan(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app) || !hasPermission(app)) return

        val startSec = System.currentTimeMillis() / 1000
        val sinceSec = prefs(app).getLong(KEY_LAST_SCAN_SEC, startSec)
        val dao = MessageDatabase.get(app).mediaDao()

        // 10 s safety margin; the unique key makes re-checking harmless.
        scanKind(app, dao, Kind.IMAGE, sinceSec = sinceSec - 10)
        scanKind(app, dao, Kind.VIDEO, sinceSec = sinceSec - 10)
        scanKind(app, dao, Kind.AUDIO, sinceSec = sinceSec - 10) // voice notes + audio files

        prefs(app).edit().putLong(KEY_LAST_SCAN_SEC, startSec).apply()
    }

    @Suppress("DEPRECATION") // MediaColumns.DATA is the only way to filter by folder on all Android versions
    private suspend fun scanKind(context: Context, dao: MediaDao, kind: Kind, sinceSec: Long) {
        val base: Uri = when (kind) {
            Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val keyPrefix = when (kind) {
            Kind.VIDEO -> "v:"
            Kind.AUDIO -> "a:"
            Kind.IMAGE -> "i:"
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA
        )
        val data = MediaStore.MediaColumns.DATA
        val pathFilter = SOURCE_PATHS.joinToString(" OR ") { "$data LIKE ?" }
        // Skip media YOU sent (".../Sent/...") - we only want received media.
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ($pathFilter) AND $data NOT LIKE ?"
        val args = arrayOf(sinceSec.toString()) + SOURCE_PATHS.toTypedArray() + arrayOf("%/Sent/%")

        val cursor = context.contentResolver.query(
            base, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} ASC"
        ) ?: return

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val key = keyPrefix + id
                if (dao.countByKey(key) > 0) continue // already saved

                val size = c.getLong(sizeCol)
                if (size <= 0L) continue                // still being written
                if (size > MAX_FILE_BYTES) {
                    Log.i(TAG, "Skipping $key: file is larger than the size limit")
                    continue
                }

                val displayName = c.getString(nameCol) ?: "media"
                val path = c.getString(pathCol).orEmpty()
                val mime = c.getString(mimeCol) ?: when (kind) {
                    Kind.VIDEO -> "video/mp4"
                    Kind.AUDIO -> "audio/ogg"
                    Kind.IMAGE -> "image/jpeg"
                }
                // What the user sees in the app: voice notes, GIFs and stickers get their own filter.
                val type = when {
                    kind == Kind.AUDIO && path.contains("Voice Notes", ignoreCase = true) -> "voice"
                    kind == Kind.AUDIO -> "audio"
                    kind == Kind.VIDEO && path.contains("Animated Gifs", ignoreCase = true) -> "gif"
                    kind == Kind.VIDEO -> "video"
                    path.contains("Stickers", ignoreCase = true) -> "sticker"
                    else -> "image"
                }
                val source = if (path.contains("w4b") || path.contains("WhatsApp Business")) {
                    "WhatsApp Business"
                } else {
                    "WhatsApp"
                }

                val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val fileName = "${kind.name.lowercase()}_${id}_$safeName"
                val uri = ContentUris.withAppendedId(base, id)

                if (copy(context, uri, MediaStorage.file(context, fileName))) {
                    dao.insert(
                        MediaRecord(
                            sourceKey = key,
                            source = source,
                            type = type,
                            mimeType = mime,
                            fileName = fileName,
                            sizeBytes = size,
                            receivedAt = c.getLong(addedCol) * 1000,
                            chatKey = guessChatKey(context, source, c.getLong(addedCol) * 1000)
                        )
                    )
                }
            }
        }
    }

    // Copies to "<name>.part" first, then renames, so a half-copied file is never kept.
    internal fun copy(context: Context, uri: Uri, dest: File): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".part")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { source -> tmp.outputStream().use { out -> source.copyTo(out) } }
            tmp.renameTo(dest)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not copy media", t)
            tmp.delete()
            false
        }
    }
}
