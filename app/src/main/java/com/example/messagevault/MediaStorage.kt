package com.example.messagevault

import android.content.Context
import java.io.File

/** Where saved photos/videos live: the app's private folder (not visible in the gallery). */
object MediaStorage {
    fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "vault_media").also { it.mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    fun delete(context: Context, fileName: String) {
        file(context, fileName).delete()
    }

    fun deleteAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
