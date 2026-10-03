package com.sundown.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.theme.*

data class SongRowModel(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationLabel: String,
    val artId: String?,
)

/**
 * `.track-row` — fixed 62 dp row.
 *
 * Phone grid: `40 | 1fr | 44`, with artist shown as a second line under the
 * title. At >=860 dp the CSS switches to `44 | 1.2fr | .8fr | 1fr | 46` with
 * artist and album as their own columns, so [wide] mirrors that.
 */
@Composable
fun SongRow(
    model: SongRowModel,
    isCurrent: Boolean,
    isPlaying: Boolean,
    wide: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
) {
    val (clickModifier, _) = rippleless(onPlay)
    val art = if (wide) D.rowArtWide else D.rowArt
    val gap = if (wide) D.rowGapWide else D.rowGap

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(D.rowHeight)
            .background(if (isCurrent && isPlaying) P.TrackPlaying else Color.Transparent),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(clickModifier)
                .padding(start = D.rowPadLeft, end = D.rowPadRight),
        ) {
            Box(
                Modifier
                    .size(art)
                    .clip(RoundedCornerShape(D.sleeveRadius))
                    .background(Color(0xFF6D7A84))
                    .border(1.dp, Color(0x8CC9D7E3), RoundedCornerShape(D.sleeveRadius)),
                contentAlignment = Alignment.Center,
            ) {
                ArtworkImage(model.artId, small = true, modifier = Modifier.fillMaxSize())
                if (isCurrent && isPlaying) {
                    Box(Modifier.fillMaxSize().background(Color(0x9E0C2130)), contentAlignment = Alignment.Center) {
                        Equalizer(true)
                    }
                }
            }

            Column(Modifier.weight(if (wide) 1.2f else 1f)) {
                Text(
                    model.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (isCurrent && isPlaying) P.TrackPlayingInk else Color(0xFFF3F6F8),
                        fontSize = if (wide) 14.cssSp else 13.5f.cssSp,
                        lineHeight = 18.cssSp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SundownFontFamily,
                        shadow = Shadow(Color(0x66182230), Offset(0f, 1f), 1f),
                    ),
                )
                if (!wide) {
                    Text(
                        model.artist.ifBlank { "Unknown Artist" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = Color(0xFFC6D3DD), fontSize = 11.5f.cssSp,
                            lineHeight = 16.cssSp, fontFamily = SundownFontFamily,
                        ),
                    )
                }
            }

            if (wide) {
                Text(
                    model.artist.ifBlank { "Unknown Artist" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.8f),
                    style = TextStyle(color = Color(0xFFD8E0E7), fontSize = 13.cssSp, lineHeight = 18.cssSp, fontFamily = SundownFontFamily),
                )
                Text(
                    model.album,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = Color(0xFFD8E0E7), fontSize = 13.cssSp, lineHeight = 18.cssSp, fontFamily = SundownFontFamily),
                )
            }

            Text(
                model.durationLabel,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.End,
                style = TextStyle(
                    color = P.InkDim, fontSize = if (wide) 13.cssSp else 12.cssSp,
                    fontFamily = SundownFontFamily,
                ),
            )
        }

        GhostIconButton(
            icon = SIcon.Plus,
            size = D.rowAdd,
            iconSize = 17.dp,
            tint = Color(0xFFBFCCD6),
            contentDescription = "Add " + model.title + " to a playlist",
            onClick = onAdd,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.RowDivider))
}

/** Sticky header above the Songs list: count on the left, Shuffle on the right. */
@Composable
fun SongsHeader(count: Int, modifier: Modifier = Modifier, onShuffle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = D.songsHeaderMinHeight)
            .background(G.songsHeader)
            .padding(start = D.songsHeaderPadLeft, end = D.songsHeaderPadRight, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            "" + count + (if (count == 1) " Song" else " Songs"),
            style = TextStyle(
                color = Color(0xFFE4EBF1), fontSize = 13.cssSp, fontWeight = FontWeight.Bold,
                fontFamily = SundownFontFamily,
                shadow = Shadow(Color(0xFF3E4F5B), Offset(0f, -1f), 0f),
            ),
        )
        SilverSmallButton(label = "Shuffle", icon = SIcon.Shuffle, enabled = count > 0, onClick = onShuffle)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x4D222E3A)))
}
