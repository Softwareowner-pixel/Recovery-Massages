package com.example.messagevault

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Contact / group pictures, taken from the big icon of the chat notification. */
object Avatars {
    private const val SIZE_PX = 128

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "avatars").also { it.mkdirs() }

    fun file(context: Context, packageName: String, chat: String): File =
        File(dir(context), Integer.toHexString((packageName + "\u0001" + chat).hashCode()) + ".png")

    /** Saves the notification's large icon once per chat. Does nothing if there is none. */
    fun saveFrom(context: Context, notification: Notification, packageName: String, chat: String) {
        if (chat.isBlank()) return
        val target = file(context, packageName, chat)
        if (target.exists()) return
        val icon = notification.getLargeIcon() ?: return
        val drawable = icon.loadDrawable(context) ?: return
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
        drawable.draw(Canvas(bitmap))
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun deleteAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}

/** The chat picture if we have one, otherwise the coloured letter. */
@Composable
fun ChatAvatar(packageName: String, chat: String, size: Dp = 48.dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, packageName, chat) {
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(Avatars.file(context, packageName, chat).path)
            } catch (_: Throwable) {
                null
            }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = chat,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Avatar(chat, size)
    }
}
