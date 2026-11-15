package com.example.messagevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val COLUMNS = 3

private val FILTERS = listOf(
    "All" to emptySet<String>(),
    "Photos" to setOf("image"),
    "Videos" to setOf("video", "gif"),
    "Voice & audio" to setOf("voice", "audio"),
    "Stickers" to setOf("sticker"),
    "Status" to setOf("status"),
    "Documents" to setOf("document")
)

/** Tab 3: saved photos, videos, voice notes, GIFs and stickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    media: List<MediaRecord>?,        // null = still loading
    mediaEnabled: Boolean,
    onOpen: (MediaRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    val wanted = FILTERS[filterIndex].second
    val shown = remember(media, filterIndex) {
        media?.filter { wanted.isEmpty() || it.type in wanted }?.chunked(COLUMNS)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FILTERS.forEachIndexed { index, (label, _) ->
                FilterChip(
                    selected = filterIndex == index,
                    onClick = { filterIndex = index },
                    label = { Text(label) }
                )
            }
        }

        when {
            shown == null -> CenteredText("Loading...")
            shown.isEmpty() -> CenteredText(
                if (mediaEnabled) {
                    "Nothing here yet.\nNew media appears after WhatsApp downloads it."
                } else {
                    "Turn on \"Save media\" in Settings to start saving.\n" +
                        "Only media that arrives after you turn it on is saved."
                }
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { row -> row.first().id }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { item ->
                            MediaThumb(
                                record = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpen(item) }
                            )
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
