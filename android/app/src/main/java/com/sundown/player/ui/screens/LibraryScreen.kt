package com.sundown.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.data.db.TrackEntity
import com.sundown.player.ui.*
import com.sundown.player.ui.components.*
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val TABS = listOf("Artists", "Albums", "Songs", "Genres", "Playlists")
private val ALPHABET = ('A'..'Z').map(Char::toString) + "#"

/**
 * The library surface: status strip, toolbar, optional scan/filter bars, the
 * active view, the A-Z rail and the bottom fade. Layout mirrors the CSS grid
 * exactly, including the phone two-row toolbar that fixed the label collision.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    playback: LibraryPlaybackState,
    onTab: (LibraryTab) -> Unit,
    onQuery: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onNewPlaylist: () -> Unit,
    onShowArtist: (String) -> Unit,
    onShowGenre: (String) -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onAddTrack: (TrackEntity) -> Unit,
    onShuffleAll: () -> Unit,
    onChooseFolder: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp.dp
    val wide = widthDp >= 860.dp
    val columns = when {
        widthDp >= 1200.dp -> 4
        widthDp >= 640.dp -> 3
        else -> 2
    }
    val cover = coverSize(widthDp)
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val songsState = rememberLazyListState()
    val artworkLoader = LocalArtworkLoader.current
    val visibleGridIndices by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.index } }
    }
    val revealedTileKeys = remember { HashSet<String>() }
    val tracksById = state.tracksById
    val albumsByGenre = state.albumsByGenre
    val galleryArtwork = remember(
        state.tab, state.albums, state.artists, state.genres, state.playlists, tracksById, albumsByGenre,
    ) {
        when (state.tab) {
            LibraryTab.Albums -> state.albums.map { item ->
                GalleryItemArtwork("album:${item.key}", listOfNotNull(item.artId))
            }
            LibraryTab.Artists -> state.artists.map { item ->
                GalleryItemArtwork("artist:${item.key}", listOfNotNull(item.artId, item.rearArtId))
            }
            LibraryTab.Genres -> state.genres.map { genre ->
                val albums = albumsByGenre[genre].orEmpty()
                GalleryItemArtwork("genre:$genre", listOfNotNull(albums.getOrNull(0)?.artId, albums.getOrNull(1)?.artId))
            }
            LibraryTab.Playlists -> state.playlists.map { playlist ->
                val artId = playlist.ids().firstNotNullOfOrNull { tracksById[it]?.artId }
                GalleryItemArtwork("playlist:${playlist.id}", listOfNotNull(artId))
            }
            LibraryTab.Songs -> emptyList()
        }
    }

    LaunchedEffect(artworkLoader, state.tab, galleryArtwork, state.prefs.highArt, columns) {
        val loader = artworkLoader ?: return@LaunchedEffect
        if (state.tab == LibraryTab.Songs) return@LaunchedEffect
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty() || galleryArtwork.isEmpty()) emptyList()
            else {
                val first = visible.minOf { it.index }.coerceAtLeast(0)
                val last = (visible.maxOf { it.index } + columns * 2).coerceAtMost(galleryArtwork.lastIndex)
                if (first > last) emptyList()
                else (first..last).flatMap { galleryArtwork.getOrNull(it)?.artIds.orEmpty() }.distinct()
            }
        }.distinctUntilChanged().collectLatest { artIds ->
            preloadArtwork(loader, artIds, small = !state.prefs.highArt)
        }
    }

    LaunchedEffect(artworkLoader, state.tab, state.songs) {
        val loader = artworkLoader ?: return@LaunchedEffect
        if (state.tab != LibraryTab.Songs) return@LaunchedEffect
        snapshotFlow {
            val visible = songsState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) emptyList()
            else {
                val first = (visible.minOf { it.index } - 1).coerceAtLeast(0)
                val last = (visible.maxOf { it.index } + 12).coerceAtMost(state.songs.size)
                if (first > last) emptyList()
                else (first..last).mapNotNull { state.songs.getOrNull(it - 1)?.artId }.distinct()
            }
        }.distinctUntilChanged().collectLatest { artIds ->
            preloadArtwork(loader, artIds, small = true)
        }
    }

    LaunchedEffect(state.query, state.tab) {
        if (state.tab == LibraryTab.Songs) songsState.scrollToItem(0)
        else gridState.scrollToItem(0)
    }

    Column(modifier.fillMaxSize()) {
        StatusStrip()
        LibraryToolbar(
            query = state.query,
            selectedTab = TABS.indexOf(state.tab.name).coerceAtLeast(0),
            wide = wide,
            onTab = { onTab(LibraryTab.valueOf(TABS[it])) },
            onQuery = onQuery,
            onClearQuery = onClearQuery,
            onOpenSources = onOpenSources,
        )
        state.scan?.let { scan ->
            ScanBar(
                title = if (scan.phase == "reading") "Reading ${scan.current}..."
                else "Reading tags ${scan.done}${if (scan.total > 0) " of ${scan.total}" else ""}...",
                subtitle = if (scan.phase == "reading") "${scan.done} audio files found"
                else scan.current.substringAfterLast('/'),
                done = scan.done,
                total = scan.total,
            )
        }
        if (state.filterActive) {
            FilterToolbar(state.filterLabel, onClearFilters)
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .cssShadow(Color(0x8716202A), blur = 6.dp, offsetY = 4.dp, cornerRadius = D.surfaceRadius)
                .clip(RoundedCornerShape(bottomStart = D.surfaceRadius, bottomEnd = D.surfaceRadius))
                .background(P.Library),
        ) {
            when {
                !state.hasLibrary && state.scan == null && state.booted -> EmptyState(
                    icon = SIcon.Folder,
                    title = "Your music, on your device",
                    body = "Choose the folder where you keep your music. Songs are read straight from your storage - nothing is uploaded.",
                    actionLabel = "Choose Music Folder",
                    onAction = onChooseFolder,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.hasLibrary && state.tab == LibraryTab.Songs && state.songs.isEmpty() -> EmptyState(
                    icon = SIcon.Search,
                    title = "Nothing matches",
                    body = "Try a different search, or clear the active filter.",
                    actionLabel = if (state.filterActive) "Show Everything" else null,
                    onAction = if (state.filterActive) onClearFilters else null,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.tab == LibraryTab.Albums && state.hasLibrary && state.albums.isEmpty() -> EmptyState(
                    icon = SIcon.Search,
                    title = "Nothing matches",
                    body = "Try a different search, or clear the active filter.",
                    actionLabel = if (state.filterActive) "Show Everything" else null,
                    onAction = if (state.filterActive) onClearFilters else null,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.tab == LibraryTab.Artists && state.hasLibrary && state.artists.isEmpty() -> EmptyState(
                    icon = SIcon.Search,
                    title = "Nothing matches",
                    body = "Try a different search, or clear the active filter.",
                    actionLabel = if (state.filterActive) "Show Everything" else null,
                    onAction = if (state.filterActive) onClearFilters else null,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.tab == LibraryTab.Genres && state.genres.isEmpty() -> EmptyState(
                    icon = SIcon.Search,
                    title = "No genres found",
                    body = "Genres come from your files' tags once music is added.",
                    modifier = Modifier.align(Alignment.Center),
                )

                state.tab == LibraryTab.Songs -> LazyColumn(state = songsState, modifier = Modifier.fillMaxSize()) {
                    stickyHeaderCompat { SongsHeader(state.songs.size, onShuffle = onShuffleAll) }
                    items(state.songs, key = { it.id }) { track ->
                        SongRow(
                            model = track.toRowModel(),
                            isCurrent = playback.trackId == track.id,
                            isPlaying = playback.playing,
                            wide = wide,
                            modifier = Modifier.padding(end = D.rowScrollInset),
                            onPlay = { onPlayTrack(track) },
                            onAdd = { onAddTrack(track) },
                        )
                    }
                    item { Spacer(Modifier.height(D.gridPadBottom)) }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = D.gridPadLeft,
                        end = D.gridPadRight,
                        top = D.gridPadTop,
                        bottom = D.gridPadBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (wide) D.gridGapYWide else D.gridGapY),
                    horizontalArrangement = Arrangement.spacedBy(if (wide) D.gridGapXWide else D.gridGapX),
                ) {
                    when (state.tab) {
                        LibraryTab.Albums -> itemsIndexed(
                            state.albums,
                            key = { _, album -> "album:${album.key}" },
                        ) { index, album ->
                            GalleryTile(
                                label = album.artist,
                                detail = album.title,
                                artId = album.artId,
                                rearArtId = null,
                                coverSize = cover,
                                index = index,
                                wide = wide,
                                revealKey = "album:${album.key}",
                                visible = index in visibleGridIndices,
                                revealedItems = revealedTileKeys,
                                smallArt = !state.prefs.highArt,
                                onClick = { onOpenAlbum(album.key) },
                            )
                        }

                        LibraryTab.Artists -> itemsIndexed(
                            state.artists,
                            key = { _, artist -> "artist:${artist.key}" },
                        ) { index, artist ->
                            GalleryTile(
                                label = artist.name,
                                detail = "${artist.albumCount} ${if (artist.albumCount == 1) "Album" else "Albums"}",
                                artId = artist.artId,
                                rearArtId = artist.rearArtId,
                                coverSize = cover,
                                index = index,
                                wide = wide,
                                revealKey = "artist:${artist.key}",
                                visible = index in visibleGridIndices,
                                revealedItems = revealedTileKeys,
                                smallArt = !state.prefs.highArt,
                                onClick = { onShowArtist(artist.key) },
                            )
                        }

                        LibraryTab.Genres -> itemsIndexed(
                            state.genres,
                            key = { _, genre -> "genre:$genre" },
                        ) { index, genre ->
                            val group = albumsByGenre[genre].orEmpty()
                            GalleryTile(
                                label = genre,
                                detail = "${group.size} ${if (group.size == 1) "Album" else "Albums"}",
                                artId = group.firstOrNull()?.artId,
                                rearArtId = group.getOrNull(1)?.artId,
                                coverSize = cover,
                                index = index,
                                wide = wide,
                                revealKey = "genre:$genre",
                                visible = index in visibleGridIndices,
                                revealedItems = revealedTileKeys,
                                smallArt = !state.prefs.highArt,
                                onClick = { onShowGenre(genre) },
                            )
                        }

                        LibraryTab.Playlists -> {
                            val newTileGridIndex = state.playlists.size +
                                if (state.query.isNotBlank() && state.playlists.isEmpty()) 1 else 0
                            itemsIndexed(
                                state.playlists,
                                key = { _, playlist -> "playlist:${playlist.id}" },
                            ) { index, playlist ->
                                val ids = playlist.ids()
                                val first = ids.firstNotNullOfOrNull { tracksById[it] }
                                GalleryTile(
                                    label = playlist.name,
                                    detail = "${ids.size} Songs",
                                    artId = first?.artId,
                                    rearArtId = null,
                                    coverSize = cover,
                                    index = index,
                                    wide = wide,
                                    revealKey = "playlist:${playlist.id}",
                                    visible = index in visibleGridIndices,
                                    revealedItems = revealedTileKeys,
                                    smallArt = !state.prefs.highArt,
                                    onClick = { onOpenPlaylist(playlist.id) },
                                )
                            }
                            if (state.query.isNotBlank() && state.playlists.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    EmptyState(
                                        icon = SIcon.Search,
                                        title = "No playlists match",
                                        body = "Clear the search to see all of your playlists.",
                                    )
                                }
                            }
                            item(key = "new-playlist") {
                                NewPlaylistTile(
                                    label = "New Playlist",
                                    detail = "Make it your own",
                                    coverSize = cover,
                                    index = state.playlists.size,
                                    wide = wide,
                                    revealKey = "new-playlist",
                                    visible = newTileGridIndex in visibleGridIndices,
                                    revealedItems = revealedTileKeys,
                                    onClick = onNewPlaylist,
                                )
                            }
                        }

                        LibraryTab.Songs -> Unit
                    }
                }
            }

            val indexVisible = state.hasLibrary && state.prefs.showIndex &&
                state.tab in listOf(LibraryTab.Albums, LibraryTab.Artists, LibraryTab.Songs)
            if (indexVisible) {
                AlphabetIndex(
                    letters = ALPHABET,
                    wide = widthDp >= 640.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = if (widthDp >= 640.dp) D.indexRightWide else D.indexRight)
                        .fillMaxHeight(0.80f),
                ) { letter ->
                    scope.launch {
                        val target = jumpTarget(state, letter)
                        if (target == null) {
                            onToast("Nothing starting with $letter.")
                        } else if (state.tab == LibraryTab.Songs) {
                            songsState.animateScrollToItem(target + 1) // +1 for the sticky header
                        } else {
                            gridState.animateScrollToItem(target)
                        }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(D.bottomFade)
                    .background(G.bottomFade()),
            )
        }
    }
}

private data class GalleryItemArtwork(val revealKey: String, val artIds: List<String>)

private suspend fun preloadArtwork(loader: ArtworkLoader, artIds: List<String>, small: Boolean) = coroutineScope {
    val permits = Semaphore(4)
    artIds.distinct().map { artId ->
        async {
            permits.withPermit {
                try {
                    loader.load(artId, small)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }.awaitAll()
}

private fun jumpTarget(state: LibraryUiState, letter: String): Int? {
    fun matches(value: String): Boolean {
        val first = value.trimStart().firstOrNull()?.lowercaseChar() ?: return false
        return if (letter == "#") !first.isLetter() else first.toString() == letter.lowercase()
    }
    val index = when (state.tab) {
        LibraryTab.Songs -> state.songs.indexOfFirst { matches(com.sundown.player.nativecore.SundownCore.sortName(it.artist)) }
        LibraryTab.Artists -> state.artists.indexOfFirst { matches(com.sundown.player.nativecore.SundownCore.sortName(it.name)) }
        else -> state.albums.indexOfFirst { matches(com.sundown.player.nativecore.SundownCore.sortName(it.artist)) }
    }
    return index.takeIf { it >= 0 }
}

fun TrackEntity.toRowModel() = SongRowModel(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationLabel = if (duration > 0) formatDuration(duration.toLong() * 1000) else "--:--",
    artId = artId,
)

/** Branded strip only; the native status bar is hidden rather than faked. */
@Composable
private fun StatusStrip() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(P.StatusBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(D.statusHeight),
    ) {
        val style = TextStyle(
            color = P.StatusInk, fontSize = 13.cssSp,
            fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
        )
        Text("Sundown", Modifier.align(Alignment.BottomStart).padding(start = D.statusNameLeft), style = style)
        BatteryGlyph(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = D.batteryRight, bottom = D.batteryBottom)
                .size(D.batteryWidth, D.batteryHeight),
        )
    }
}

@Composable
private fun BatteryGlyph(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val unit = size.width / 25f
        val stroke = 1.2f * unit
        drawRoundRect(
            color = P.BatteryInk,
            topLeft = Offset(unit, unit),
            size = androidx.compose.ui.geometry.Size(21 * unit, 10 * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * unit, 1.5f * unit),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawRect(P.BatteryInk, Offset(23 * unit, 4 * unit), androidx.compose.ui.geometry.Size(2 * unit, 4 * unit))
        drawRect(P.BatteryInk, Offset(3 * unit, 3 * unit), androidx.compose.ui.geometry.Size(17 * unit, 6 * unit))
    }
}

/** `.library-toolbar` — two rows on phones, one row at >=860 dp. */
@Composable
private fun LibraryToolbar(
    query: String,
    selectedTab: Int,
    wide: Boolean,
    onTab: (Int) -> Unit,
    onQuery: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenSources: () -> Unit,
) {
    val config = LocalConfiguration.current
    val narrow = config.screenWidthDp <= 360

    Column(
        Modifier
            .fillMaxWidth()
            .background(G.toolbar)
            .padding(
                start = if (wide) D.toolbarPadHWide else D.toolbarPadH,
                end = if (wide) D.toolbarPadHWide else D.toolbarPadH,
                top = D.toolbarPadTop,
                bottom = D.toolbarPadBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(D.toolbarGap),
    ) {
        if (wide) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(D.toolbarPadHWide),
            ) {
                MetalButton("Sources", icon = SIcon.Library, onClick = onOpenSources)
                SegmentedControl(TABS, selectedTab, Modifier.weight(1f), fontSize = 14f, onSelect = onTab)
                SearchPill(query, Modifier.width(D.searchWidthWide), onValueChange = onQuery, onClear = onClearQuery)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(D.toolbarGap),
            ) {
                MetalButton(
                    label = if (narrow) null else "Sources",
                    icon = SIcon.Library,
                    modifier = Modifier.widthIn(max = 150.dp),
                    onClick = onOpenSources,
                )
                Spacer(Modifier.weight(1f))
                SearchPill(
                    query,
                    Modifier.widthIn(max = 230.dp).weight(1f, fill = false).width(
                        (config.screenWidthDp * 0.54f).dp.coerceAtMost(230.dp),
                    ),
                    onValueChange = onQuery,
                    onClear = onClearQuery,
                )
            }
            SegmentedControl(
                TABS, selectedTab, Modifier.fillMaxWidth(),
                fontSize = if (narrow) 11.5f else 13f, onSelect = onTab,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(P.ToolbarBottomEdge))
}

@Composable
private fun FilterToolbar(label: String, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = D.filterMinHeight)
            .background(Color(0x1F2A3845))
            .padding(horizontal = D.filterPadH, vertical = D.filterPadV),
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = P.Ink, fontSize = 16.cssSp, fontWeight = FontWeight.Bold,
                fontFamily = SundownFontFamily,
                shadow = Shadow(Color(0xFF3E4C57), Offset(0f, -1f), 0f),
            ),
        )
        GhostIconButton(SIcon.Close, D.filterClear, 16.dp, P.InkDim, "Show all music", onClick = onClear)
    }
}

/** Sticky header shim so the Songs list keeps its pinned count + Shuffle bar. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.stickyHeaderCompat(
    content: @Composable () -> Unit,
) = stickyHeader { content() }
