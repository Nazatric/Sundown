package com.sundown.player.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.playback.PlayerSnapshot
import com.sundown.player.ui.components.*
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.theme.*

/** Shared transport cluster: prev / play-pause / next. */
@Composable
fun TransportRow(
    snapshot: PlayerSnapshot,
    big: Boolean,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    val size = if (big) D.transportWide else D.transport
    val playSize = if (big) D.transportPlayWide else D.transportPlay
    val icon = if (big) D.transportIconWide else D.transportIcon
    val playIcon = if (big) D.transportPlayIconWide else D.transportPlayIcon
    val enabled = snapshot.hasSource

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (big) 16.dp else D.transportGap),
        modifier = modifier,
    ) {
        TransportButton(SIcon.Previous, size, icon, "Previous song", enabled = enabled, onClick = onPrevious)
        TransportButton(
            icon = if (snapshot.playing) SIcon.Pause else SIcon.Play,
            diameter = playSize,
            iconSize = playIcon,
            contentDescription = if (snapshot.playing) "Pause" else "Play",
            enabled = enabled,
            busy = snapshot.loading,
            onClick = onToggle,
        )
        TransportButton(SIcon.Next, size, icon, "Next song", enabled = enabled, onClick = onNext)
    }
}

@Composable
fun Timeline(
    snapshot: PlayerSnapshot,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit,
) {
    val duration = snapshot.durationMs
    val fraction = if (duration > 0) snapshot.elapsedMs.toFloat() / duration else 0f
    val elapsedLabel = formatDuration(snapshot.elapsedMs)
    val remainingLabel = if (duration > 0) "-" + formatDuration(duration - snapshot.elapsedMs) else "-:--"
    val style = TextStyle(
        color = Color(0xFFD3DBE1), fontSize = 11.5f.cssSp, fontFamily = SundownFontFamily,
        shadow = Shadow(Color(0xFF27323B), Offset(0f, -1f), 1f),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D.timelineGap),
        modifier = modifier,
    ) {
        Text(
            elapsedLabel,
            Modifier.widthIn(min = if (elapsedLabel.count { it == ':' } > 1) 50.dp else D.elapsedWidth),
            style = style,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        ProgressSlider(fraction, Modifier.weight(1f), enabled = snapshot.hasSource, onScrub = onScrub)
        Text(
            remainingLabel,
            Modifier.widthIn(min = if (remainingLabel.count { it == ':' } > 1) 54.dp else D.remainingWidth),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * `.player` — the docked mini player.
 *
 * Phone: art | meta | transport, with the timeline spanning the row below.
 * At >=860 dp the CSS adds the mode pill and volume, so this does too.
 */
@Composable
fun MiniPlayer(
    snapshot: PlayerSnapshot,
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onScrub: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 860
    val landscape = config.screenHeightDp <= 560 &&
        config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val height = when {
        landscape -> D.playerHeightLandscape
        wide -> D.playerHeightWide
        else -> D.playerHeight
    }
    val artSize = if (wide) D.npArtWide else D.npArt

    Column(
        modifier
            .fillMaxWidth()
            .background(G.player)
            .drawBehind {
                drawLine(
                    color = Color(0xFF52606B),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(height)
            .padding(start = D.playerPadH, top = D.playerPadTop, end = D.playerPadH, bottom = D.playerPadBottom),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            val (artClick, _) = rippleless(onOpenNowPlaying)
            Box(
                Modifier
                    .size(artSize)
                    .cssShadow(Color(0x99111921), blur = 4.dp, offsetY = 2.dp, cornerRadius = D.npArtRadius)
                    .clip(RoundedCornerShape(D.npArtRadius))
                    .background(G.npArt)
                    .border(1.dp, Color(0xFF16222B), RoundedCornerShape(D.npArtRadius))
                    .then(artClick)
                    .padding(2.dp),
            ) {
                ArtworkImage(
                    snapshot.artId,
                    small = true,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)),
                )
                if (snapshot.playing) NowPlayingPulse(Modifier.matchParentSize())
            }

            val (metaClick, _) = rippleless(onOpenNowPlaying)
            Column(Modifier.weight(1f).then(metaClick)) {
                Text(
                    snapshot.title.ifBlank { "No music loaded" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Color(0xFFF4F7F9), fontSize = if (wide) 15.cssSp else 13.5f.cssSp,
                        lineHeight = if (wide) 19.cssSp else 17.cssSp,
                        fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                        shadow = Shadow(Color(0xFF2C3842), Offset(0f, -1f), 0f),
                    ),
                )
                Text(
                    snapshot.artist.ifBlank {
                        if (snapshot.hasSource) "Unknown Artist" else "Choose your music folder to begin"
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = P.InkMuted, fontSize = if (wide) 13.cssSp else 12.cssSp,
                        lineHeight = 16.cssSp, fontFamily = SundownFontFamily,
                    ),
                )
            }

            if (wide) {
                ModePill(snapshot.repeat, snapshot.shuffle, onRepeat = onRepeat, onShuffle = onShuffle)
            }
            TransportRow(snapshot, big = wide, onPrevious = onPrevious, onToggle = onToggle, onNext = onNext)
            if (wide) {
                VolumePill(
                    snapshot.volume, snapshot.muted, Modifier.width(200.dp),
                    onLevel = onVolume, onToggleMute = onMute,
                )
            }
        }
        Timeline(snapshot, Modifier.fillMaxWidth(), onScrub = onScrub)
    }
}

/** `.np-art-pulse` — 1.8 s breathing ring while audio is playing. */
@Composable
private fun NowPlayingPulse(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier
            .padding(2.dp)
            .border(1.dp, Color(0xFF78D2FF).copy(alpha = alpha), RoundedCornerShape(1.dp)),
    )
}

/** Full Now Playing sheet: stage, metadata, scrubber, transport, modes, volume. */
@Composable
fun NowPlayingContent(
    snapshot: PlayerSnapshot,
    modifier: Modifier = Modifier,
    onOpenAlbum: () -> Unit,
    onOpenQueue: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onScrub: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    val config = LocalConfiguration.current
    val landscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val stage = if (landscape) minOf(config.screenHeightDp * 0.46f, 260f).dp
    else minOf(config.screenWidthDp * 0.72f, 320f).dp

    if (!snapshot.hasSource) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            com.sundown.player.ui.icons.SundownIcon(SIcon.Music, 46.dp, Color(0xFFC3D2DD), strokeWidth = 1.4f)
            Text(
                "Nothing playing",
                style = TextStyle(color = P.Ink, fontSize = 19.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
            )
            Text(
                "Pick a song from your library and it will show up here.",
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
                style = TextStyle(color = Color(0xFFC6D4DE), fontSize = 13.cssSp, lineHeight = 20.cssSp, fontFamily = SundownFontFamily),
            )
        }
        return
    }

    val breathe = rememberInfiniteTransition(label = "breathe")
    val scale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = if (snapshot.playing) 1.012f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 18.dp, bottom = 4.dp).scale(scale)) {
            AlbumStack(snapshot.artId, null, stage)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.title,
                    style = TextStyle(
                        color = P.Ink, fontSize = 21.cssSp, lineHeight = 25.cssSp,
                        fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                        shadow = Shadow(Color(0xFF31434F), Offset(0f, -1f), 0f),
                    ),
                )
                Text(
                    snapshot.artist.ifBlank { "Unknown Artist" } + " · " + snapshot.album,
                    style = TextStyle(
                        color = Color(0xFFD2DEE7), fontSize = 13.5f.cssSp, lineHeight = 19.cssSp,
                        fontFamily = SundownFontFamily,
                    ),
                )
                if (snapshot.year > 0) {
                    Text(
                        snapshot.year.toString() + snapshot.genre.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                        modifier = Modifier.padding(top = 3.dp),
                        style = TextStyle(color = Color(0xFFAEBFCC), fontSize = 12.cssSp, lineHeight = 16.cssSp, fontFamily = SundownFontFamily),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(SIcon.Disc, "Open this album", onOpenAlbum),
                    Triple(SIcon.Library, "Up next", onOpenQueue),
                ).forEach { (icon, label, click) ->
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(G.silver)
                            .border(1.dp, Color(0xFF2C4152), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GhostIconButton(icon, 42.dp, 20.dp, Color(0xFF344B5B), label, onClick = click)
                    }
                }
            }
        }

        Timeline(snapshot, Modifier.fillMaxWidth(), onScrub = onScrub)
        TransportRow(snapshot, big = true, onPrevious = onPrevious, onToggle = onToggle, onNext = onNext)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModePill(snapshot.repeat, snapshot.shuffle, onRepeat = onRepeat, onShuffle = onShuffle)
            VolumePill(
                snapshot.volume, snapshot.muted, Modifier.weight(1f).widthIn(max = 240.dp),
                onLevel = onVolume, onToggleMute = onMute,
            )
        }

        Text(
            "Playing your original file without conversion.",
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color(0xFFA9BCC9), fontSize = 11.cssSp, lineHeight = 16.cssSp, fontFamily = SundownFontFamily),
        )
    }
}
