package com.sundown.player.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sundown.player.data.db.PlaylistEntity
import com.sundown.player.data.db.TrackEntity
import com.sundown.player.ui.AlbumGroup
import com.sundown.player.ui.components.*
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.icons.SundownIcon
import com.sundown.player.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * `.sheet` — bottom-anchored panel with a dimmed, tap-to-dismiss scrim.
 *
 * Presented over the library rather than replacing it, exactly like the web
 * `<dialog>`; the navigation back stack still owns dismissal so the system
 * back gesture pops one sheet at a time.
 */
@Composable
fun SundownSheet(
    onDismiss: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(closeSheet: () -> Unit, closeSheetThen: (afterClose: () -> Unit) -> Unit) -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    val dismissRequested = remember { mutableStateOf(false) }
    val dismissCompleted = remember { mutableStateOf(false) }
    val afterDismiss = remember { mutableStateOf<(() -> Unit)?>(null) }
    val backProgress = remember { Animatable(0f) }
    val backGestureActive = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(visibility) { visibility.targetState = true }

    val requestDismiss: ((() -> Unit)?) -> Unit = remember {
        { afterClose: (() -> Unit)? ->
            if (!dismissRequested.value) {
                afterDismiss.value = afterClose
                dismissRequested.value = true
                backGestureActive.value = false
                visibility.targetState = false
            }
        }
    }
    val closeSheet: () -> Unit = remember(requestDismiss) { { requestDismiss(null) } }
    val closeSheetThen: (() -> Unit) -> Unit = remember(requestDismiss) {
        { afterClose -> requestDismiss(afterClose) }
    }

    LaunchedEffect(
        visibility.currentState,
        visibility.targetState,
        visibility.isIdle,
        dismissRequested.value,
    ) {
        if (dismissRequested.value && visibility.isIdle && !visibility.currentState && !dismissCompleted.value) {
            dismissCompleted.value = true
            if (latestOnDismiss()) {
                afterDismiss.value?.invoke()
            } else {
                dismissRequested.value = false
                dismissCompleted.value = false
                visibility.targetState = true
            }
        }
    }

    // Keep the handler scoped to the active sheet: a dialog still owns Back
    // first, and Navigation Compose handles routes when no sheet is on top.
    PredictiveBackHandler(enabled = true) { events ->
        try {
            events.collect { event ->
                if (!dismissRequested.value) {
                    backGestureActive.value = true
                    backProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
            }
            if (!dismissRequested.value) {
                backGestureActive.value = true
                backProgress.snapTo(1f)
                if (latestOnDismiss()) {
                    dismissRequested.value = true
                    dismissCompleted.value = true
                } else {
                    scope.launch {
                        backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        backGestureActive.value = false
                    }
                }
            }
        } catch (_: CancellationException) {
            if (!dismissRequested.value) {
                scope.launch {
                    backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                    backGestureActive.value = false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            Box(Modifier.fillMaxSize()) {
                val (scrimClick, _) = rippleless(closeSheet, enabled = !dismissRequested.value)
                Box(
                    Modifier
                        .fillMaxSize()
                        .animateEnterExit(enter = fadeIn(tween(200)), exit = fadeOut(tween(150)))
                        .graphicsLayer {
                            alpha = if (backGestureActive.value) 1f - backProgress.value else 1f
                        }
                        .background(P.Scrim)
                        .then(scrimClick),
                )
                Column(
                    modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(
                                animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f)),
                                initialOffsetY = { it / 4 },
                            ) + fadeIn(tween(160, easing = CubicBezierEasing(0.2f, 0.75f, 0.25f, 1f))),
                            exit = slideOutVertically(tween(180), targetOffsetY = { it / 4 }) + fadeOut(tween(140)),
                        )
                        .graphicsLayer {
                            if (backGestureActive.value) {
                                translationY = backProgress.value * size.height
                                alpha = 1f - backProgress.value
                            } else {
                                translationY = 0f
                                alpha = 1f
                            }
                        }
                        .fillMaxWidth()
                        .heightIn(max = 780.dp)
                        .cssShadow(Color(0x99000A15), blur = 50.dp, offsetY = (-10).dp, cornerRadius = D.sheetRadius)
                        .clip(RoundedCornerShape(topStart = D.sheetRadius, topEnd = D.sheetRadius))
                        .background(P.SheetBg)
                        .border(
                            1.dp, P.SheetBorder,
                            RoundedCornerShape(topStart = D.sheetRadius, topEnd = D.sheetRadius),
                        )
                        // Keep sheet content clear of gesture navigation and the keyboard
                        // while the panel surface remains edge-to-edge.
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding(),
                ) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(D.sheetGrabW, D.sheetGrabH)
                            .clip(RoundedCornerShape(3.dp))
                            .background(P.SheetGrab),
                    )
                    content(closeSheet, closeSheetThen)
                }
            }
        }
        if (dismissRequested.value) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
            )
        }
    }
}

/** `.sheet-toolbar` — optional back button, centred title, close affordance. */
@Composable
fun SheetToolbar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = D.sheetBarMinHeight)
            .background(G.sheetBar)
            .padding(horizontal = D.sheetBarPadH, vertical = D.sheetBarPadV),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (backLabel != null && onBack != null) {
                MetalButton(backLabel, icon = SIcon.Chevron, onClick = onBack)
            }
        }
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(2f),
            style = TextStyle(
                color = P.Ink, fontSize = 16.cssSp, fontWeight = FontWeight.Bold,
                fontFamily = SundownFontFamily,
                shadow = Shadow(Color(0xFF3D5263), Offset(0f, -1f), 1f),
            ),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (trailing != null) trailing()
            else GhostIconButton(SIcon.Close, D.sheetClose, D.sheetCloseIcon, Color(0xFFF0F5F9), "Close", onClick = onClose)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.SheetDivider))
}

@Composable
fun SheetSectionTitle(text: String, detail: String? = null) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.weight(1f, fill = false),
            style = TextStyle(
                color = Color(0xFFDFE9F1), fontSize = 12.cssSp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp, fontFamily = SundownFontFamily,
                shadow = Shadow(Color(0xFF3A4E5E), Offset(0f, -1f), 0f),
            ),
        )
        if (detail != null) {
            Text(
                detail,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
                style = TextStyle(color = Color(0xFFA9BCC9), fontSize = 10.cssSp, fontFamily = SundownFontFamily),
            )
        }
    }
}

/** A row in `.sheet-actions`. */
@Composable
fun SheetAction(
    label: String,
    icon: SIcon,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val (clickModifier, _) = rippleless(onClick, enabled)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = D.sheetActionMinHeight)
            .then(clickModifier)
            .padding(horizontal = D.sheetActionPadH, vertical = D.sheetActionPadV),
    ) {
        SundownIcon(icon, D.sheetActionIcon, if (danger) Color(0xFFDFB9B9) else Color(0xFFB9CBDA))
        Text(
            label,
            style = TextStyle(
                color = when {
                    danger -> P.Danger
                    highlight -> Color(0xFFBFE4FF)
                    else -> Color(0xFFEEF4F8)
                },
                fontSize = 14.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
            ),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66253949)))
}

/** `.album-track` — numbered row used in album and playlist sheets. */
@Composable
fun SheetTrackRow(
    index: Int,
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    showArtwork: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
) {
    val (clickModifier, _) = rippleless(onPlay)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = D.albumTrackMinHeight)
            .background(if (isCurrent && isPlaying) P.TrackPlaying else Color.Transparent),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .then(clickModifier)
                .padding(horizontal = D.albumTrackPadH, vertical = D.albumTrackPadV),
        ) {
            if (showArtwork) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                    ArtworkImage(track.artId, small = true, modifier = Modifier.fillMaxSize())
                    if (isCurrent && isPlaying) {
                        Box(Modifier.fillMaxSize().background(Color(0x9E0C2130)), contentAlignment = Alignment.Center) {
                            Equalizer(true)
                        }
                    }
                }
            } else {
                Box(Modifier.width(D.albumTrackNo), contentAlignment = Alignment.Center) {
                    if (isCurrent && isPlaying) {
                        Equalizer(true)
                    } else {
                        Text(
                            (if (track.trackNo > 0) track.trackNo else index + 1).toString(),
                            style = TextStyle(color = Color(0xFFC3CDD6), fontSize = 12.5f.cssSp, fontFamily = SundownFontFamily),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = if (isCurrent && isPlaying) P.TrackPlayingInk else Color(0xFFF1F5F8),
                        fontSize = 13.5f.cssSp, lineHeight = 18.cssSp,
                        fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                    ),
                )
                if (showArtwork) {
                    Text(
                        track.artist,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Color(0xFFB9C8D3), fontSize = 11.cssSp, lineHeight = 15.cssSp, fontFamily = SundownFontFamily),
                    )
                }
            }
            Text(
                if (track.duration > 0) formatDuration(track.duration.toLong() * 1000) else "--:--",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(color = Color(0xFFCFDAE2), fontSize = 12.cssSp, fontFamily = SundownFontFamily),
            )
        }
        GhostIconButton(SIcon.Plus, 40.dp, 17.dp, Color(0xFFBFCCD6), "Add to a playlist", onClick = onAdd)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66243746)))
}

/** Album detail sheet. */
@Composable
fun AlbumSheetContent(
    album: AlbumGroup,
    favorite: Boolean,
    currentTrackId: String?,
    playing: Boolean,
    onClose: () -> Unit,
    onPlay: (TrackEntity) -> Unit,
    onAdd: (TrackEntity) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    SheetToolbar(album.artist, onClose, backLabel = "Library", onBack = onClose)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = D.albumHeadingPadH, vertical = D.albumHeadingPadV),
            horizontalArrangement = Arrangement.spacedBy(D.albumHeadingGap),
        ) {
            Box(
                Modifier
                    .size(D.detailArt)
                    .cssShadow(Color(0x82162D2D), blur = 7.dp, offsetY = 3.dp, cornerRadius = 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0xA6DBE4EB), RoundedCornerShape(2.dp)),
            ) {
                ArtworkImage(album.artId, small = false, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Text(
                    album.title,
                    style = TextStyle(
                        color = P.Ink, fontSize = 20.cssSp, lineHeight = 24.cssSp,
                        fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                    ),
                )
                Text(
                    buildString {
                        if (album.year > 0) append("${album.year} · ")
                        append("${album.tracks.size} ${if (album.tracks.size == 1) "Song" else "Songs"}")
                        if (album.genre.isNotBlank()) append(" · ${album.genre}")
                    },
                    modifier = Modifier.padding(top = 7.dp),
                    style = TextStyle(color = Color(0xFFD0DCE5), fontSize = 12.5f.cssSp, lineHeight = 18.cssSp, fontFamily = SundownFontFamily),
                )
                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val first = album.tracks.firstOrNull()
                    val isThis = first != null && first.id == currentTrackId && playing
                    SilverSmallButton(
                        label = if (isThis) "Pause" else "Play Album",
                        icon = if (isThis) SIcon.Pause else SIcon.Play,
                        enabled = first != null,
                        onClick = { first?.let(onPlay) },
                    )
                    SilverSmallButton(
                        label = null, icon = SIcon.Star, selected = favorite,
                        width = D.favoriteWidth, onClick = onToggleFavorite,
                    )
                }
            }
        }
        album.tracks.forEachIndexed { index, track ->
            SheetTrackRow(
                index = index,
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = playing,
                showArtwork = false,
                onPlay = { onPlay(track) },
                onAdd = { onAdd(track) },
            )
        }
        Text(
            "Songs play from your local files exactly as stored on your device.",
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 22.dp),
            style = TextStyle(color = Color(0xFFB9C9D4), fontSize = 11.5f.cssSp, lineHeight = 17.cssSp, fontFamily = SundownFontFamily),
        )
    }
}

/** Playlist detail sheet. */
@Composable
fun PlaylistSheetContent(
    playlist: PlaylistEntity,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    playing: Boolean,
    onClose: () -> Unit,
    onPlay: (TrackEntity) -> Unit,
    onAdd: (TrackEntity) -> Unit,
    onDelete: () -> Unit,
) {
    SheetToolbar("Playlists", onClose, backLabel = "Playlists", onBack = onClose)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(color = P.Ink, fontSize = 20.cssSp, lineHeight = 24.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
                )
                Text(
                    "${tracks.size} ${if (tracks.size == 1) "Song" else "Songs"}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(color = Color(0xFFCCDAE4), fontSize = 12.5f.cssSp, fontFamily = SundownFontFamily),
                )
            }
            SilverSmallButton(
                label = "Play", icon = SIcon.Play, enabled = tracks.isNotEmpty(),
                onClick = { tracks.firstOrNull()?.let(onPlay) },
            )
        }
        if (tracks.isEmpty()) {
            Text(
                "Use the + next to any song to add it to this playlist.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
                style = TextStyle(color = Color(0xFFCFDAE3), fontSize = 13.cssSp, lineHeight = 20.cssSp, fontFamily = SundownFontFamily),
            )
        } else {
            tracks.forEachIndexed { index, track ->
                SheetTrackRow(index, track, track.id == currentTrackId, playing, showArtwork = true,
                    onPlay = { onPlay(track) }, onAdd = { onAdd(track) })
            }
        }
        if (playlist.custom) {
            Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 26.dp), contentAlignment = Alignment.Center) {
                val (clickModifier, _) = rippleless(onDelete)
                Text(
                    "Delete Playlist",
                    modifier = Modifier.then(clickModifier).padding(8.dp),
                    style = TextStyle(
                        color = Color(0xFFEED5D5), fontSize = 13.cssSp, fontFamily = SundownFontFamily,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                )
            }
        }
    }
}

/** Add-to-playlist chooser. */
@Composable
fun ChooserSheetContent(
    track: TrackEntity,
    playlists: List<PlaylistEntity>,
    playlistTrackIdSets: Map<String, Set<String>>,
    onClose: () -> Unit,
    onChoose: (PlaylistEntity) -> Unit,
    onCreateNew: () -> Unit,
) {
    SheetToolbar("Add to Playlist", onClose)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                track.title, textAlign = TextAlign.Center,
                style = TextStyle(color = P.Ink, fontSize = 15.5f.cssSp, lineHeight = 21.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
            )
            Text(
                track.artist, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
                style = TextStyle(color = Color(0xFFC7D7E2), fontSize = 12.cssSp, fontFamily = SundownFontFamily),
            )
        }
        playlists.forEach { playlist ->
            val ids = playlistTrackIdSets[playlist.id].orEmpty()
            val included = track.id in ids
            val (clickModifier, _) = rippleless({ onChoose(playlist) }, enabled = !included)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(clickModifier)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(color = Color(0xFFEDF4F8), fontSize = 14.cssSp, lineHeight = 19.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
                    )
                    Text(
                        if (included) "Already added" else "${ids.size} songs",
                        style = TextStyle(color = Color(0xFFC6D6E1), fontSize = 11.5f.cssSp, lineHeight = 17.cssSp, fontFamily = SundownFontFamily),
                    )
                }
                SundownIcon(if (included) SIcon.Check else SIcon.Plus, 18.dp, Color(0xFFEDF4F8))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF405869)))
        }
        val (createClick, _) = rippleless(onCreateNew)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .background(Color(0xFF526B7D))
                .then(createClick),
        ) {
            SundownIcon(SIcon.Plus, 16.dp, Color(0xFFEFF7FC))
            Text(
                "New Playlist",
                style = TextStyle(color = Color(0xFFEFF7FC), fontSize = 14.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
            )
        }
    }
}
