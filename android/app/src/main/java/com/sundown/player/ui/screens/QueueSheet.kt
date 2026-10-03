package com.sundown.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.data.db.TrackEntity
import com.sundown.player.ui.components.ArtworkImage
import com.sundown.player.ui.components.Equalizer
import com.sundown.player.ui.components.GhostIconButton
import com.sundown.player.ui.components.rippleless
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.theme.P
import com.sundown.player.ui.theme.SundownFontFamily
import com.sundown.player.ui.theme.cssSp

/**
 * Up Next.
 *
 * The web build kept a queue internally but never surfaced it. On a phone the
 * queue is genuinely useful, so it gets a real screen: tap to jump, swipe-free
 * remove button, and the playing row marked with the same equalizer used
 * elsewhere.
 */
@Composable
fun QueueSheetContent(
    queue: List<TrackEntity>,
    currentTrackId: String?,
    playing: Boolean,
    onClose: () -> Unit,
    onJump: (TrackEntity) -> Unit,
    onRemove: (TrackEntity) -> Unit,
    onClear: () -> Unit,
) {
    SheetToolbar(
        title = "Up Next",
        onClose = onClose,
        trailing = {
            GhostIconButton(SIcon.Trash, 34.dp, 16.dp, Color(0xFFDFB9B9), "Clear queue", onClick = onClear)
        },
    )

    if (queue.isEmpty()) {
        Text(
            "Nothing queued yet. Play an album or a playlist to fill this up.",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 40.dp),
            style = TextStyle(
                color = Color(0xFFCFDAE3), fontSize = 13.cssSp, lineHeight = 20.cssSp,
                fontFamily = SundownFontFamily,
            ),
        )
        return
    }

    val currentIndex = queue.indexOfFirst { it.id == currentTrackId }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
            val isCurrent = track.id == currentTrackId
            val upcoming = currentIndex >= 0 && index > currentIndex
            val (clickModifier, _) = rippleless({ onJump(track) })

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (isCurrent) P.TrackPlaying else Color.Transparent)
                    .then(clickModifier)
                    .padding(start = 18.dp, end = 6.dp),
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                    ArtworkImage(track.artId, small = true, modifier = Modifier.fillMaxSize())
                    if (isCurrent && playing) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0x9E0C2130)),
                            contentAlignment = Alignment.Center,
                        ) { Equalizer(true) }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (isCurrent) P.TrackPlayingInk else P.Ink,
                            fontSize = 13.5f.cssSp, lineHeight = 18.cssSp,
                            fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                        ),
                    )
                    Text(
                        track.artist.ifBlank { "Unknown Artist" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = if (upcoming) Color(0xFFC0D0DB) else Color(0xFF9FB2BF),
                            fontSize = 11.5f.cssSp, lineHeight = 16.cssSp,
                            fontFamily = SundownFontFamily,
                        ),
                    )
                }
                GhostIconButton(
                    SIcon.Close, 38.dp, 14.dp, Color(0xFFAEBDC8),
                    "Remove ${track.title} from the queue",
                    onClick = { onRemove(track) },
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x5F253949)))
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
