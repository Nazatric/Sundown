package com.sundown.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sundown.player.data.db.TrackEntity
import com.sundown.player.nav.Route
import com.sundown.player.playback.PlayerSnapshot
import com.sundown.player.ui.components.ConfirmSundownDialog
import com.sundown.player.ui.components.NoticeToast
import com.sundown.player.ui.screens.*
import com.sundown.player.ui.theme.D
import com.sundown.player.ui.theme.P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

/** Ignore taps from a sheet that is already leaving the back stack. */
private fun popEntry(nav: NavHostController, entry: NavBackStackEntry): Boolean =
    nav.currentBackStackEntry?.id == entry.id && nav.popBackStack()

private fun dismissEntry(nav: NavHostController, entry: NavBackStackEntry): () -> Unit = {
    popEntry(nav, entry)
}

@Composable
fun SundownRoot(
    vm: LibraryViewModel,
    state: LibraryUiState,
    playback: PlayerSnapshot,
    libraryPlayback: LibraryPlaybackState,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onGrantMediaAccess: () -> Unit,
) {
    val nav = rememberNavController()
    val activeEntry by nav.currentBackStackEntryAsState()
    val activeRoute = activeEntry?.destination?.route
    val sheetVisible = activeRoute != null && activeRoute != Route.Library.path && activeRoute != Route.Filtered.path
    var confirmation by remember { mutableStateOf<ConfirmRequest?>(null) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenHeightDp <= 560
    val playerHeight = when {
        landscape -> D.playerHeightLandscape
        configuration.screenWidthDp >= 860 -> D.playerHeightWide
        else -> D.playerHeight
    }
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(P.AppBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (sheetVisible) Modifier.blur(2.dp) else Modifier),
        ) {
            LibraryScreen(
                state = state,
                playback = libraryPlayback,
                modifier = Modifier.weight(1f),
                onTab = vm::selectTab,
                onQuery = vm::setQuery,
                onClearQuery = vm::clearQuery,
                onClearFilters = vm::clearFilters,
                onOpenSources = { nav.navigate(Route.Sources.path) { launchSingleTop = true } },
                onGrantMediaAccess = onGrantMediaAccess,
                onOpenAlbum = { key -> nav.navigate(Route.Album.of(key)) },
                onOpenPlaylist = { id -> nav.navigate(Route.Playlist.of(id)) },
                onNewPlaylist = { nav.navigate(Route.newPlaylist()) },
                onShowArtist = { key -> vm.showArtist(key); nav.navigate(Route.Filtered.artist(key)) },
                onShowGenre = { name -> vm.showGenre(name); nav.navigate(Route.Filtered.genre(name)) },
                onPlayTrack = { track -> vm.playTracks(state.songs, track.id) },
                onAddTrack = { track -> nav.navigate(Route.Chooser.of(track.id)) },
                onShuffleAll = { vm.shuffleAll(state.songs) },
                onChooseFolder = onPickFolder,
                onToast = vm::toast,
            )
            MiniPlayer(
                snapshot = playback,
                onOpenNowPlaying = { nav.navigate(Route.NowPlaying.path) },
                onPrevious = { vm.player.previous(); vm.persistPlaybackState() },
                onToggle = { vm.player.toggle(); vm.persistPlaybackState() },
                onNext = { vm.player.next(); vm.persistPlaybackState() },
                onScrub = vm.player::seekFraction,
                onVolume = { vm.player.setVolume(it); vm.persistPlaybackState() },
                onMute = { vm.player.toggleMute(); vm.persistPlaybackState() },
                onShuffle = { vm.player.toggleShuffle(); vm.persistPlaybackState() },
                onRepeat = { vm.player.cycleRepeat(); vm.persistPlaybackState() },
            )
        }

        SheetHost(
            nav = nav,
            vm = vm,
            state = state,
            playback = playback,
            onPickFolder = onPickFolder,
            onPickFiles = onPickFiles,
            onGrantMediaAccess = onGrantMediaAccess,
            onConfirm = { confirmation = it },
        )

        state.notice?.let { notice ->
            LaunchedEffect(notice.id) {
                delay(4_500)
                vm.dismissNotice()
            }
            NoticeToast(
                message = notice.message,
                onDismiss = vm::dismissNotice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = playerHeight + navigationInset + D.noticeBottomGap)
                    .padding(horizontal = 13.dp),
            )
        }

        confirmation?.let { request ->
            ConfirmSundownDialog(
                title = request.title,
                message = request.message,
                confirmLabel = request.confirmLabel,
                onDismiss = { confirmation = null },
                onConfirm = {
                    confirmation = null
                    request.onConfirm()
                },
            )
        }
    }
}

@Composable
private fun SheetHost(
    nav: NavHostController,
    vm: LibraryViewModel,
    state: LibraryUiState,
    playback: PlayerSnapshot,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onGrantMediaAccess: () -> Unit,
    onConfirm: (ConfirmRequest) -> Unit,
) {
    val backStackEntry by nav.currentBackStackEntryAsState()

    if (backStackEntry?.destination?.route == Route.Library.path && state.filterActive) {
        // Search is state rather than a screen, so one Back press clears it
        // before Android leaves the top-level library destination.
        BackHandler { vm.clearFilters() }
    }

    NavHost(navController = nav, startDestination = Route.Library.path) {
        composable(Route.Library.path) {}

        composable(
            Route.Filtered.path,
            arguments = listOf(
                navArgument("artist") { type = NavType.StringType; defaultValue = "" },
                navArgument("genre") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            BackHandler { vm.clearFilters(); nav.popBackStack() }
        }

        composable(Route.Sources.path) { entry ->
            val dismiss = dismissEntry(nav, entry)
            val treeUri = state.prefs.treeUri
            var folderAccess by remember(treeUri) { mutableStateOf<Boolean?>(if (treeUri == null) false else null) }
            LaunchedEffect(treeUri) {
                folderAccess = if (treeUri == null) false else vm.hasFolderAccess(treeUri)
            }
            SundownSheet(onDismiss = dismiss) {
                SourcesSheetContent(
                    prefs = state.prefs,
                    trackCount = state.tracks.size,
                    albumCount = state.allAlbums.size,
                    sessionCount = state.sessionTrackCount,
                    hasAccess = folderAccess == true,
                    checkingFolderAccess = treeUri != null && folderAccess == null,
                    mediaStorePermission = state.mediaStorePermission,
                    scanning = state.scan != null,
                    onClose = dismiss,
                    onPickFolder = { dismiss(); onPickFolder() },
                    onRestoreAccess = { dismiss(); onPickFolder() },
                    onRescan = { dismiss(); vm.rescan() },
                    onDisconnect = { dismiss(); vm.disconnectFolder() },
                    onAddFiles = { dismiss(); onPickFiles() },
                    onGrantMediaAccess = { dismiss(); onGrantMediaAccess() },
                    onScanDeviceMusic = { dismiss(); vm.rescanDeviceMusic() },
                    onSettings = { update -> vm.updateSettings(update); Unit },
                    onClearArtwork = { dismiss(); vm.clearArtwork() },
                    onEraseAll = {
                        onConfirm(
                            ConfirmRequest(
                                title = "Erase all library data?",
                                message = "Erase all library data, playlists and artwork cached by this app? Your music files are not touched.",
                                confirmLabel = "Erase",
                                onConfirm = { dismiss(); vm.eraseEverything() },
                            ),
                        )
                    },
                )
            }
        }

        composable(Route.NowPlaying.path) { entry ->
            val dismiss = dismissEntry(nav, entry)
            SundownSheet(onDismiss = dismiss) {
                NowPlayingContent(
                    snapshot = playback,
                    onOpenAlbum = {
                        val track = playback.trackId?.let(state.tracksById::get)
                        if (track != null && popEntry(nav, entry)) {
                            nav.navigate(Route.Album.of(track.albumKey))
                        }
                    },
                    onOpenQueue = { nav.navigate(Route.Queue.path) },
                    onPrevious = { vm.player.previous(); vm.persistPlaybackState() },
                    onToggle = { vm.player.toggle(); vm.persistPlaybackState() },
                    onNext = { vm.player.next(); vm.persistPlaybackState() },
                    onScrub = vm.player::seekFraction,
                    onVolume = { vm.player.setVolume(it); vm.persistPlaybackState() },
                    onMute = { vm.player.toggleMute(); vm.persistPlaybackState() },
                    onShuffle = { vm.player.toggleShuffle(); vm.persistPlaybackState() },
                    onRepeat = { vm.player.cycleRepeat(); vm.persistPlaybackState() },
                )
            }
        }

        composable(Route.Queue.path) { entry ->
            val dismiss = dismissEntry(nav, entry)
            SundownSheet(onDismiss = dismiss) {
                QueueSheetContent(
                    queue = vm.queueTracks(),
                    currentTrackId = playback.trackId,
                    playing = playback.playing,
                    onClose = dismiss,
                    onJump = vm::jumpToQueued,
                    onRemove = vm::removeFromQueue,
                    onClear = { vm.clearQueue(); dismiss() },
                )
            }
        }

        composable(
            Route.Album.path,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) { entry ->
            val dismiss = dismissEntry(nav, entry)
            val key = entry.arguments?.getString("key").orEmpty()
            val album = state.allAlbumsByKey[key]
            if (album == null) {
                if (state.booted) LaunchedEffect(key, entry.id) { popEntry(nav, entry) }
            } else {
                SundownSheet(onDismiss = dismiss) {
                    AlbumSheetContent(
                        album = album,
                        favorite = album.key in state.favorites,
                        currentTrackId = playback.trackId,
                        playing = playback.playing,
                        onClose = dismiss,
                        onPlay = { track -> vm.playTracks(album.tracks, track.id) },
                        onAdd = { track -> nav.navigate(Route.Chooser.of(track.id)) },
                        onToggleFavorite = { vm.toggleFavorite(album.key) },
                    )
                }
            }
        }

        composable(
            Route.Playlist.path,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val dismiss = dismissEntry(nav, entry)
            val id = entry.arguments?.getString("id").orEmpty()
            val playlist = state.playlistsById[id]
            if (playlist == null) {
                if (state.booted) LaunchedEffect(id, entry.id) { popEntry(nav, entry) }
            } else {
                var tracks by remember(id) { mutableStateOf(emptyList<TrackEntity>()) }
                LaunchedEffect(id, playlist.trackIds, state.tracksById) {
                    tracks = withContext(Dispatchers.Default) {
                        playlist.ids().mapNotNull(state.tracksById::get)
                    }
                }
                SundownSheet(onDismiss = dismiss) {
                    PlaylistSheetContent(
                        playlist = playlist,
                        tracks = tracks,
                        currentTrackId = playback.trackId,
                        playing = playback.playing,
                        onClose = dismiss,
                        onPlay = { track -> vm.playTracks(tracks, track.id) },
                        onAdd = { track -> nav.navigate(Route.Chooser.of(track.id)) },
                        onDelete = {
                            onConfirm(
                                ConfirmRequest(
                                    title = "Delete this playlist?",
                                    message = "Your music files are not affected.",
                                    confirmLabel = "Delete",
                                    onConfirm = { vm.deletePlaylist(playlist.id); dismiss() },
                                ),
                            )
                        },
                    )
                }
            }
        }

        composable(
            Route.Chooser.path,
            arguments = listOf(navArgument("trackId") { type = NavType.StringType }),
        ) { entry ->
            val dismiss = dismissEntry(nav, entry)
            val trackId = entry.arguments?.getString("trackId").orEmpty()
            val track = state.tracksById[trackId]
            if (track == null) {
                if (state.booted) LaunchedEffect(trackId, entry.id) { popEntry(nav, entry) }
            } else {
                SundownSheet(onDismiss = dismiss) {
                    ChooserSheetContent(
                        track = track,
                        playlists = state.playlists,
                        playlistTrackIdSets = state.playlistTrackIdSetsById,
                        onClose = dismiss,
                        onChoose = { playlist -> vm.addToPlaylist(playlist, track.id); dismiss() },
                        onCreateNew = {
                            if (popEntry(nav, entry)) nav.navigate(Route.newPlaylist(track.id))
                        },
                    )
                }
            }
        }

        composable(
            Route.NewPlaylist.path,
            arguments = listOf(navArgument("seed") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val dismiss = dismissEntry(nav, entry)
            val seed = entry.arguments?.getString("seed").orEmpty()
            SundownSheet(onDismiss = dismiss) {
                NewPlaylistContent(
                    tracks = state.tracks,
                    seedIds = if (seed.isBlank()) emptyList() else listOf(seed),
                    onClose = dismiss,
                    onCreate = { name, ids ->
                        vm.savePlaylist(null, name, ids)
                        dismiss()
                    },
                )
            }
        }
    }
}
