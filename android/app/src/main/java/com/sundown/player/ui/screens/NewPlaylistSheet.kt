package com.sundown.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sundown.player.ui.components.*
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.icons.SundownIcon
import com.sundown.player.ui.theme.*

private data class PlaylistSortableTrack(
    val track: TrackEntity,
    val artist: String,
    val title: String,
)

/**
 * `.new-playlist-sheet` — name field, song filter, and a checklist.
 * The list is lazy so choosing from a few thousand tracks stays smooth.
 */
@Composable
fun NewPlaylistContent(
    tracks: List<TrackEntity>,
    seedIds: List<String>,
    onClose: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    val selection = remember { mutableStateListOf<String>().apply { addAll(seedIds) } }

    var sortedTracks by remember(tracks) { mutableStateOf(emptyList<TrackEntity>()) }
    LaunchedEffect(tracks) {
        sortedTracks = withContext(Dispatchers.Default) {
            tracks.map { track ->
                PlaylistSortableTrack(track, track.artist.lowercase(), track.title.lowercase())
            }.sortedWith(compareBy<PlaylistSortableTrack>({ it.artist }, { it.title }))
                .map(PlaylistSortableTrack::track)
        }
    }

    var visible by remember { mutableStateOf(emptyList<TrackEntity>()) }
    LaunchedEffect(sortedTracks, filter) {
        visible = withContext(Dispatchers.Default) {
            val needle = filter.trim()
            if (needle.isBlank()) sortedTracks
            else sortedTracks.filter { "${it.title} ${it.artist} ${it.album}".contains(needle, true) }
        }
    }

    SheetToolbar(
        title = "New Playlist",
        onClose = onClose,
        backLabel = "Cancel",
        onBack = onClose,
        trailing = {
            MetalButton(
                label = "Create",
                enabled = name.isNotBlank(),
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), selection.toList()) },
            )
        },
    )

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                "Playlist Name",
                modifier = Modifier.padding(bottom = 7.dp),
                style = TextStyle(color = Color(0xFFE0EAF2), fontSize = 12.5f.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFDFE7ED), Color(0xFFF3F6F9))))
                    .border(1.dp, Color(0xFF334A5B), RoundedCornerShape(5.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) {
                    Text(
                        "Give it a name",
                        style = TextStyle(color = Color(0xFF8C9DAA), fontSize = 16.cssSp, fontFamily = SundownFontFamily),
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFF354958)),
                    textStyle = TextStyle(color = Color(0xFF354958), fontSize = 16.cssSp, fontFamily = SundownFontFamily),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(Color(0xFFE9EEF2))
                .border(1.dp, Color(0xFF334A5B), RoundedCornerShape(19.dp))
                .padding(horizontal = 10.dp),
        ) {
            SundownIcon(SIcon.Search, 15.dp, Color(0xFF7D8C98), strokeWidth = 2.5f)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (filter.isEmpty()) {
                    Text(
                        "Find songs to include",
                        style = TextStyle(color = Color(0xFF8C9DAA), fontSize = 14.cssSp, fontFamily = SundownFontFamily),
                    )
                }
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    cursorBrush = SolidColor(Color(0xFF3A4C5A)),
                    textStyle = TextStyle(color = Color(0xFF3A4C5A), fontSize = 14.cssSp, fontFamily = SundownFontFamily),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            "${selection.size} selected",
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
            style = TextStyle(color = Color(0xFFC4D4DF), fontSize = 12.cssSp, fontFamily = SundownFontFamily),
        )

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(visible, key = { it.id }) { track ->
                val checked = track.id in selection
                val (clickModifier, _) = rippleless({
                    if (checked) selection.remove(track.id) else selection.add(track.id)
                })
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(clickModifier)
                        .padding(horizontal = 18.dp),
                ) {
                    Box(
                        Modifier
                            .size(17.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (checked) P.AccentDeep else Color(0x33FFFFFF))
                            .border(1.dp, Color(0xFF9FB2C0), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (checked) SundownIcon(SIcon.Check, 12.dp, Color.White, strokeWidth = 3f)
                    }
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(2.dp))) {
                        ArtworkImage(track.artId, small = true, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = P.Ink, fontSize = 13.5f.cssSp, lineHeight = 18.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
                        )
                        Text(
                            track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Color(0xFFC0D0DB), fontSize = 11.5f.cssSp, lineHeight = 16.cssSp, fontFamily = SundownFontFamily),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x5F253949)))
            }
        }
    }
}
