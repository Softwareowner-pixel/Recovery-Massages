package com.example.messagevault

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isAudioType(type: String): Boolean = type == "voice" || type == "audio"

fun mediaTypeLabel(type: String): String = when (type) {
    "video" -> "Video"
    "gif" -> "GIF"
    "voice" -> "Voice note"
    "audio" -> "Audio"
    "sticker" -> "Sticker"
    "document" -> "Document"
    "status" -> "Status"
    else -> "Photo"
}

/** Original file name without our "type_id_" prefix. */
fun displayFileName(record: MediaRecord): String =
    record.fileName.split("_", limit = 3).getOrElse(2) { record.fileName }

/** The "Save media" switch card (photos, videos, GIFs, stickers, voice notes). */
@Composable
fun MediaSettingsCard(
    enabled: Boolean,
    permitted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Save media", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Copies new WhatsApp photos, videos, GIFs, stickers and voice notes into this app, " +
                            "so they stay even if the sender deletes them. Only files WhatsApp has " +
                            "already downloaded can be saved, so keep auto-download ON in WhatsApp. " +
                            "Files over ${MediaCapture.MAX_FILE_BYTES / (1024 * 1024)} MB are skipped.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled && !permitted) {
                Text(
                    "Permission is missing. Allow access to ALL photos, videos and music/audio, " +
                        "otherwise nothing is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { onToggle(true) }) { Text("Grant permission") }
            }
        }
    }
}

/** One square tile. Photos/videos show a thumbnail, voice notes show an icon. */
@Composable
fun MediaThumb(record: MediaRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val isVideo = record.type == "video" || record.type == "gif" ||
        (record.type == "status" && record.mimeType.startsWith("video"))
    val isAudio = isAudioType(record.type)
    val isDocument = record.type == "document"
    val bitmap by produceState<Bitmap?>(null, record.fileName) {
        value = if (isAudio || isDocument) {
            null
        } else {
            withContext(Dispatchers.IO) {
                Thumbnails.load(MediaStorage.file(context, record.fileName), isVideo, 360)
            }
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = mediaTypeLabel(record.type),
                contentScale = if (record.type == "sticker") ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isAudio) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (record.type == "voice") "🎤" else "🎵", style = MaterialTheme.typography.headlineMedium)
                Text(
                    Formatter.formatShortFileSize(context, record.sizeBytes),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (isDocument) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                Text("📄", style = MaterialTheme.typography.headlineMedium)
                Text(
                    displayFileName(record),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isVideo) {
            Text(
                "▶",
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(10.dp)
            )
        }
    }
}

/** What opens when a saved item is tapped. Voice notes / audio can be played right here. */
@Composable
fun MediaDialog(
    item: MediaRecord,
    onOpenExternally: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun togglePlay() {
        if (playing) {
            player?.release()
            player = null
            playing = false
            return
        }
        var created: MediaPlayer? = null
        try {
            val mp = MediaPlayer()
            created = mp
            mp.setDataSource(MediaStorage.file(context, item.fileName).path)
            mp.setOnCompletionListener {
                it.release()
                player = null
                playing = false
            }
            mp.prepare()
            mp.start()
            player = mp
            playing = true
        } catch (t: Throwable) {
            created?.release()
            Toast.makeText(context, "Cannot play this file here. Try \"Open with...\".", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mediaTypeLabel(item.type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.type == "document") Text(displayFileName(item), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${item.source} • ${formatDateTime(item.receivedAt)} • " +
                        Formatter.formatShortFileSize(context, item.sizeBytes)
                )
                if (isAudioType(item.type)) {
                    Button(onClick = { togglePlay() }) {
                        Text(if (playing) "⏹ Stop" else "▶ Play")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenExternally()
            }) { Text("Open with...") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    onDelete()
                }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
