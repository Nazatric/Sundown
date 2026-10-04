package com.sundown.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sundown.player.data.ArtworkStore
import com.sundown.player.data.LibraryRepository
import com.sundown.player.data.ScanProgress
import com.sundown.player.data.SearchIndex
import com.sundown.player.data.matchesSearch
import com.sundown.player.data.db.PlaylistEntity
import com.sundown.player.data.db.TrackEntity
import com.sundown.player.data.prefs.Prefs
import com.sundown.player.data.prefs.SundownPrefs
import com.sundown.player.nativecore.SundownCore
import com.sundown.player.playback.PlaybackProgress
import com.sundown.player.playback.PlayerController
import com.sundown.player.playback.PlayerSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class LibraryTab { Artists, Albums, Songs, Genres, Playlists }

data class AlbumGroup(
    val key: String,
    val title: String,
    val artist: String,
    val artistKey: String,
    val year: Int,
    val genre: String,
    val artId: String?,
    val tracks: List<TrackEntity>,
)

data class ArtistGroup(
    val key: String,
    val name: String,
    val albums: List<AlbumGroup>,
    val artId: String?,
    val rearArtId: String?,
) {
    val albumCount get() = albums.size
}

data class Notice(val id: Long, val message: String)

data class LibraryUiState(
    val booted: Boolean = false,
    val tab: LibraryTab = LibraryTab.Albums,
    val query: String = "",
    val genreFilter: String? = null,
    val artistFilter: String? = null,
    val favoritesOnly: Boolean = false,
    val tracks: List<TrackEntity> = emptyList(),
    val sessionTrackCount: Int = 0,
    val allAlbums: List<AlbumGroup> = emptyList(),
    val allAlbumsByKey: Map<String, AlbumGroup> = emptyMap(),
    val albums: List<AlbumGroup> = emptyList(),
    val allArtists: List<ArtistGroup> = emptyList(),
    val allArtistsByKey: Map<String, ArtistGroup> = emptyMap(),
    val artists: List<ArtistGroup> = emptyList(),
    val tracksById: Map<String, TrackEntity> = emptyMap(),
    val albumsByGenre: Map<String, List<AlbumGroup>> = emptyMap(),
    val genres: List<String> = emptyList(),
    val songs: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistsById: Map<String, PlaylistEntity> = emptyMap(),
    val playlistTrackCountsById: Map<String, Int> = emptyMap(),
    val playlistTrackIdSetsById: Map<String, Set<String>> = emptyMap(),
    val playlistFirstArtIdById: Map<String, String?> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val prefs: Prefs = Prefs(),
    val scan: ScanProgress? = null,
    val notice: Notice? = null,
    val mediaStorePermission: Boolean = false,
) {
    val hasLibrary get() = tracks.isNotEmpty()
    val filterActive get() = genreFilter != null || artistFilter != null || favoritesOnly || query.isNotBlank()
    val filterLabel: String
        get() = when {
            favoritesOnly -> "Favorites"
            genreFilter != null -> genreFilter
            artistFilter != null -> allArtistsByKey[artistFilter]?.name.orEmpty()
            else -> ""
        }
}

@OptIn(FlowPreview::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val prefsStore = SundownPrefs(app)
    val artwork = ArtworkStore(app)
    private val repo = LibraryRepository(app, prefsStore, artwork)
    private val mediaStorePermission = MutableStateFlow(repo.mediaStorePermissionGranted)
    private val searchIndex = SearchIndex(app)
    val player = PlayerController(app, artwork)
    val libraryPlaybackState: StateFlow<LibraryPlaybackState> = player.state
        .map { LibraryPlaybackState(trackId = it.trackId, playing = it.playing) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryPlaybackState())

    private val tab = MutableStateFlow(LibraryTab.Albums)
    private val query = MutableStateFlow("")
    private val genreFilter = MutableStateFlow<String?>(null)
    private val artistFilter = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val notice = MutableStateFlow<Notice?>(null)
    private val booted = MutableStateFlow(false)

    val playerState: StateFlow<PlayerSnapshot> get() = player.state
    val playbackProgress: StateFlow<PlaybackProgress> get() = player.progress
    val queueState: StateFlow<List<TrackEntity>> get() = player.queueState

    private data class UserFilters(
        val genre: String?,
        val artist: String?,
        val favoritesOnly: Boolean,
    )

    private data class ContentFilters(
        val search: String,
        val genre: String?,
        val artist: String?,
        val favoritesOnly: Boolean,
    )

    private data class LocalState(
        val tab: LibraryTab,
        val query: String,
        val filters: UserFilters,
        val notice: Notice?,
        val booted: Boolean,
    )

    private data class LibraryContent(
        val tracks: List<TrackEntity> = emptyList(),
        val sortedTracks: List<TrackEntity> = emptyList(),
        val sessionTrackCount: Int = 0,
        val allAlbums: List<AlbumGroup> = emptyList(),
        val allAlbumsByKey: Map<String, AlbumGroup> = emptyMap(),
        val allArtists: List<ArtistGroup> = emptyList(),
        val allArtistsByKey: Map<String, ArtistGroup> = emptyMap(),
        val genres: List<String> = emptyList(),
        val tracksById: Map<String, TrackEntity> = emptyMap(),
        val albumsByGenre: Map<String, List<AlbumGroup>> = emptyMap(),
    )

    private data class SortableTrack(
        val track: TrackEntity,
        val artist: String,
        val album: String,
        val discNo: Int,
        val trackNo: Int,
        val title: String,
    )

    private data class PlaylistContent(
        val playlists: List<PlaylistEntity> = emptyList(),
        val trackIdsById: Map<String, List<String>> = emptyMap(),
        val trackCountsById: Map<String, Int> = emptyMap(),
        val trackIdSetsById: Map<String, Set<String>> = emptyMap(),
    )

    private data class PlaylistPresentation(
        val playlists: List<PlaylistEntity> = emptyList(),
        val trackCountsById: Map<String, Int> = emptyMap(),
        val trackIdSetsById: Map<String, Set<String>> = emptyMap(),
        val firstArtIdById: Map<String, String?> = emptyMap(),
    )

    private data class FilteredContent(
        val tracks: List<TrackEntity> = emptyList(),
        val sessionTrackCount: Int = 0,
        val allAlbums: List<AlbumGroup> = emptyList(),
        val allAlbumsByKey: Map<String, AlbumGroup> = emptyMap(),
        val albums: List<AlbumGroup> = emptyList(),
        val allArtists: List<ArtistGroup> = emptyList(),
        val allArtistsByKey: Map<String, ArtistGroup> = emptyMap(),
        val artists: List<ArtistGroup> = emptyList(),
        val genres: List<String> = emptyList(),
        val tracksById: Map<String, TrackEntity> = emptyMap(),
        val albumsByGenre: Map<String, List<AlbumGroup>> = emptyMap(),
        val songs: List<TrackEntity> = emptyList(),
        val playlists: List<PlaylistEntity> = emptyList(),
        val playlistsById: Map<String, PlaylistEntity> = emptyMap(),
        val playlistTrackCountsById: Map<String, Int> = emptyMap(),
        val playlistTrackIdSetsById: Map<String, Set<String>> = emptyMap(),
        val playlistFirstArtIdById: Map<String, String?> = emptyMap(),
        val favorites: Set<String> = emptySet(),
    )

    private val sortNameCache = ConcurrentHashMap<String, String>()

    private val debouncedSearch: StateFlow<String> = query
        .map { it.trim().lowercase() }
        .debounce(160)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val userFilters: Flow<UserFilters> = combine(genreFilter, artistFilter, favoritesOnly) { genre, artist, favorites ->
        UserFilters(genre, artist, favorites)
    }.distinctUntilChanged()

    private val contentFilters: Flow<ContentFilters> = combine(debouncedSearch, userFilters) { search, filters ->
        ContentFilters(search, filters.genre, filters.artist, filters.favoritesOnly)
    }.distinctUntilChanged()

    private val localState: Flow<LocalState> = combine(tab, query, userFilters, notice, booted) { selectedTab, rawQuery, filters, currentNotice, isBooted ->
        LocalState(selectedTab, rawQuery, filters, currentNotice, isBooted)
    }

    /**
     * Room invalidations are the only inputs that rebuild grouping/sort indexes.
     * Progress, playback checkpoints and transient notices cannot redo this work.
     * The FTS index is synchronized from this exact snapshot before it becomes
     * visible to search, so fast queries can use FTS without an O(library-size)
     * pending-row reconciliation on every keystroke.
     */
    private val libraryContent: StateFlow<LibraryContent> = repo.tracks
        .distinctUntilChanged()
        // A media scan commits bounded batches. Collapse rapid Room invalidations
        // before rebuilding the full grouping/sort maps for a large library.
        .conflate()
        .debounce(160)
        .map { tracks ->
            withContext(Dispatchers.IO) { searchIndex.synchronize(tracks) }
            buildLibraryContent(tracks)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryContent())

    /** Parse serialized playlist IDs once per Room playlist snapshot, not per search keystroke. */
    private val playlistContent: StateFlow<PlaylistContent> = repo.playlists
        .distinctUntilChanged()
        .map { playlists ->
            val idsById = playlists.associate { playlist -> playlist.id to playlist.ids() }
            PlaylistContent(
                playlists = playlists,
                trackIdsById = idsById,
                trackCountsById = idsById.mapValues { (_, ids) -> ids.size },
                trackIdSetsById = idsById.mapValues { (_, ids) -> ids.toHashSet() },
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistContent())

    /** Resolve cover references only when Room tracks or playlist data actually changes. */
    private val playlistPresentation: StateFlow<PlaylistPresentation> = combine(
        playlistContent,
        libraryContent.map { it.tracksById }.distinctUntilChanged(),
    ) { playlists, tracksById ->
        PlaylistPresentation(
            playlists = playlists.playlists,
            trackCountsById = playlists.trackCountsById,
            trackIdSetsById = playlists.trackIdSetsById,
            firstArtIdById = playlists.trackIdsById.mapValues { (_, ids) ->
                ids.firstNotNullOfOrNull { tracksById[it]?.artId }
            },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistPresentation())

    /** Checkpoint writes update playback fields every couple seconds; the UI state
     * intentionally projects those out so only actual UI preferences invalidate it. */
    private val uiPrefs: StateFlow<Prefs> = prefsStore.flow
        .map { prefs ->
            prefs.copy(
                volume = 0.8f,
                muted = false,
                shuffle = false,
                repeat = "off",
                queue = emptyList(),
                currentId = null,
                position = 0L,
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    private val favoriteSet: Flow<Set<String>> = uiPrefs.map { it.favorites }.distinctUntilChanged()

    private val filteredContent: StateFlow<FilteredContent> = combine(
        libraryContent,
        playlistPresentation,
        contentFilters,
        favoriteSet,
    ) { library, playlists, filters, favorites ->
        // Search touches only playlist names. Serialized track IDs, membership
        // sets, counts and artwork picks are cached per Room snapshot above.
        val visiblePlaylists = if (filters.search.isBlank()) playlists.playlists
            else playlists.playlists.filter { it.name.contains(filters.search, ignoreCase = true) }
        FilteredContent(
            tracks = library.tracks,
            sessionTrackCount = library.sessionTrackCount,
            allAlbums = library.allAlbums,
            allAlbumsByKey = library.allAlbumsByKey,
            albums = filterAlbums(library.allAlbums, filters, favorites),
            allArtists = library.allArtists,
            allArtistsByKey = library.allArtistsByKey,
            artists = filterArtists(library.allArtists, filters, favorites),
            genres = if (filters.search.isBlank()) library.genres
                else library.genres.filter { it.contains(filters.search, ignoreCase = true) },
            tracksById = library.tracksById,
            albumsByGenre = library.albumsByGenre,
            songs = filterSongs(library.sortedTracks, library.tracks, filters, favorites),
            playlists = visiblePlaylists,
            playlistsById = visiblePlaylists.associateBy(PlaylistEntity::id),
            playlistTrackCountsById = playlists.trackCountsById,
            playlistTrackIdSetsById = playlists.trackIdSetsById,
            playlistFirstArtIdById = playlists.firstArtIdById,
            favorites = favorites,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FilteredContent())

    val state: StateFlow<LibraryUiState> = combine(
        filteredContent, uiPrefs, repo.progress, localState, mediaStorePermission,
    ) { content, prefs, scan, local, permissionGranted ->
        LibraryUiState(
            booted = local.booted,
            tab = local.tab,
            query = local.query,
            genreFilter = local.filters.genre,
            artistFilter = local.filters.artist,
            favoritesOnly = local.filters.favoritesOnly,
            tracks = content.tracks,
            sessionTrackCount = content.sessionTrackCount,
            allAlbums = content.allAlbums,
            allAlbumsByKey = content.allAlbumsByKey,
            albums = content.albums,
            allArtists = content.allArtists,
            allArtistsByKey = content.allArtistsByKey,
            artists = content.artists,
            tracksById = content.tracksById,
            albumsByGenre = content.albumsByGenre,
            genres = content.genres,
            songs = content.songs,
            playlists = content.playlists,
            playlistsById = content.playlistsById,
            playlistTrackCountsById = content.playlistTrackCountsById,
            playlistTrackIdSetsById = content.playlistTrackIdSetsById,
            playlistFirstArtIdById = content.playlistFirstArtIdById,
            favorites = content.favorites,
            prefs = prefs,
            scan = scan,
            notice = local.notice,
            mediaStorePermission = permissionGranted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            repo.messages.collect { message -> toast(message) }
        }
        viewModelScope.launch {
            repo.mediaStoreChanges
                .debounce(750)
                .catch { error ->
                    if (error !is CancellationException) {
                        toast(error.message ?: "Automatic device-library updates are unavailable.")
                    }
                }
                .collect {
                    if (repo.mediaStorePermissionGranted) {
                        repo.rescanMediaStore(waitForScan = true, notify = false)
                    }
                }
        }
        viewModelScope.launch {
            try {
                val prefs = prefsStore.flow.first()
                tab.value = runCatching { LibraryTab.valueOf(prefs.defaultTab) }.getOrDefault(LibraryTab.Albums)
                booted.value = true
                if (prefs.autoRescan && repo.hasFolderAccess(prefs.treeUri)) {
                    prefs.treeUri?.let { repo.rescan(Uri.parse(it)) }
                }
                if (prefs.autoRescan && repo.mediaStorePermissionGranted) {
                    repo.rescanMediaStore()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                toast(error.message ?: "Sundown could not load the saved library.")
                booted.value = true
            }
        }

        player.beginQueueRestoration()
        player.onError = ::toast
        player.onDurationResolved = { id, seconds ->
            viewModelScope.launch {
                try {
                    repo.updateDuration(id, seconds)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A duration refresh is opportunistic; a failed write is retried on a later scan.
                }
            }
        }
        player.connect {
            viewModelScope.launch { restorePlayback() }
        }

        // The service owns playback when the UI is backgrounded. While the app
        // is visible, persist a checkpoint so process death resumes near the
        // last position rather than at the beginning of a track.
        viewModelScope.launch {
            player.progress.sample(2_000).collect { progress ->
                val snapshot = player.state.value
                if (snapshot.hasSource) persistSnapshot(snapshot, progress)
            }
        }
    }

    private suspend fun restorePlayback() {
        try {
            val prefs = prefsStore.flow.first()
            player.restoreVolume(prefs.volume, prefs.muted)
            player.setShuffleEnabled(prefs.shuffle)
            player.setRepeatMode(prefs.repeat)

            val allTracks = repo.tracks.first()
            // A MediaSession can outlive this Activity/ViewModel. Reattach Room
            // metadata to that live queue instead of replacing the song that is
            // already playing with an older DataStore checkpoint.
            if (!player.attachTracks(allTracks)) {
                val byId = allTracks.associateBy(TrackEntity::id)
                val queue = prefs.queue.mapNotNull(byId::get).distinctBy(TrackEntity::id).toMutableList()
                val current = prefs.currentId?.let(byId::get)
                if (current != null && queue.none { it.id == current.id }) queue.add(0, current)
                if (queue.isNotEmpty()) {
                    val index = queue.indexOfFirst { it.id == prefs.currentId }.takeIf { it >= 0 } ?: 0
                    player.restore(queue, index, prefs.position.coerceAtLeast(0L))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            toast(error.message ?: "The saved playback queue could not be restored.")
        }
        if (player.finishQueueRestoration()) persistSnapshot(player.state.value)
    }

    // ---- grouping and filtering --------------------------------------------

    private fun buildLibraryContent(tracks: List<TrackEntity>): LibraryContent {
        val allAlbums = groupAlbums(tracks)
        val allArtists = groupArtists(allAlbums)
        val genres = tracks.mapNotNull { it.genre.takeIf(String::isNotBlank) }.distinct().sorted()
        val trackSortNames = tracks.asSequence()
            .map(TrackEntity::artist)
            .distinct()
            .associateWith(::sortName)
        val sortNameByTrackId = tracks.associate { it.id to (trackSortNames[it.artist] ?: "") }
        // The Songs tab has a fixed order. Sort once per Room snapshot rather
        // than re-running an O(n log n) comparator on every search keystroke.
        val sortedTracks = tracks.map { track ->
            SortableTrack(
                track = track,
                artist = sortNameByTrackId[track.id].orEmpty(),
                album = track.album.lowercase(),
                discNo = track.discNo,
                trackNo = track.trackNo,
                title = track.title.lowercase(),
            )
        }.sortedWith(compareBy<SortableTrack>({ it.artist }, { it.album }, { it.discNo }, { it.trackNo }, { it.title }))
            .map(SortableTrack::track)

        return LibraryContent(
            tracks = tracks,
            sortedTracks = sortedTracks,
            // Sources must not walk the full library on the main thread just to
            // count individually selected files every time its sheet recomposes.
            sessionTrackCount = tracks.count { it.source == LibraryRepository.SOURCE_FILE },
            allAlbums = allAlbums,
            allAlbumsByKey = allAlbums.associateBy(AlbumGroup::key),
            allArtists = allArtists,
            allArtistsByKey = allArtists.associateBy(ArtistGroup::key),
            genres = genres,
            tracksById = tracks.associateBy(TrackEntity::id),
            albumsByGenre = allAlbums.filter { it.genre.isNotBlank() }.groupBy(AlbumGroup::genre),
        )
    }

    private fun sortName(name: String): String = sortNameCache.computeIfAbsent(name) { SundownCore.sortName(it) }

    private fun groupAlbums(tracks: List<TrackEntity>): List<AlbumGroup> {
        val grouped = LinkedHashMap<String, MutableList<TrackEntity>>()
        tracks.forEach { grouped.getOrPut(it.albumKey) { mutableListOf() }.add(it) }
        return grouped.map { (key, items) ->
            val first = items.first()
            AlbumGroup(
                key = key,
                title = first.album.ifBlank { "Unknown Album" },
                artist = first.albumArtist.ifBlank { first.artist }.ifBlank { "Unknown Artist" },
                artistKey = first.artistKey,
                year = items.firstOrNull { it.year > 0 }?.year ?: 0,
                genre = items.firstOrNull { it.genre.isNotBlank() }?.genre.orEmpty(),
                artId = items.firstNotNullOfOrNull(TrackEntity::artId),
                tracks = items.sortedWith(compareBy({ it.discNo }, { it.trackNo }, { it.title.lowercase() })),
            )
        }.sortedWith(compareBy({ sortName(it.artist) }, { it.title.lowercase() }))
    }

    private fun groupArtists(albums: List<AlbumGroup>): List<ArtistGroup> =
        albums.groupBy(AlbumGroup::artistKey).map { (key, items) ->
            val ordered = items.sortedWith(compareByDescending<AlbumGroup> { it.year }.thenBy { it.title.lowercase() })
            ArtistGroup(
                key = key,
                name = ordered.first().artist,
                albums = ordered,
                artId = ordered.firstNotNullOfOrNull(AlbumGroup::artId),
                rearArtId = ordered.drop(1).firstNotNullOfOrNull(AlbumGroup::artId),
            )
        }.sortedBy { sortName(it.name) }

    private fun filterAlbums(albums: List<AlbumGroup>, filter: ContentFilters, favorites: Set<String>) = albums.filter { album ->
        (filter.artist == null || album.artistKey == filter.artist) &&
            (filter.genre == null || album.genre == filter.genre) &&
            (!filter.favoritesOnly || album.key in favorites) &&
            (filter.search.isBlank() || "${album.title} ${album.artist}".contains(filter.search, true))
    }

    private fun filterArtists(artists: List<ArtistGroup>, filter: ContentFilters, favorites: Set<String>) = artists.filter { artist ->
        (filter.genre == null || artist.albums.any { it.genre == filter.genre }) &&
            (!filter.favoritesOnly || artist.albums.any { it.key in favorites }) &&
            (filter.search.isBlank() || artist.name.contains(filter.search, true) ||
                artist.albums.any { it.title.contains(filter.search, true) })
    }

    private fun filterSongs(
        sortedTracks: List<TrackEntity>,
        searchSnapshot: List<TrackEntity>,
        filter: ContentFilters,
        favorites: Set<String>,
    ): List<TrackEntity> {
        val ftsIds = if (filter.search.isBlank()) null else searchIndex.search(filter.search, searchSnapshot)
        // sortedTracks preserves the library's stable sort order, so filtering
        // is linear and does not re-sort the full result on every query change.
        return sortedTracks.filter { track ->
            (filter.artist == null || track.artistKey == filter.artist) &&
                (filter.genre == null || track.genre == filter.genre) &&
                (!filter.favoritesOnly || track.albumKey in favorites) &&
                (filter.search.isBlank() ||
                    ((ftsIds == null || track.id in ftsIds) && track.matchesSearch(filter.search)))
        }
    }

    // ---- UI intents ---------------------------------------------------------

    fun selectTab(next: LibraryTab) { tab.value = next }
    fun setQuery(value: String) { query.value = value }
    fun clearQuery() { query.value = "" }
    fun showArtist(key: String) { artistFilter.value = key; tab.value = LibraryTab.Albums }
    fun showGenre(name: String) { genreFilter.value = name; tab.value = LibraryTab.Albums }
    fun clearFilters() {
        genreFilter.value = null
        artistFilter.value = null
        favoritesOnly.value = false
        query.value = ""
    }

    fun dismissNotice() { notice.value = null }
    fun toast(message: String) { notice.value = Notice(System.currentTimeMillis(), message) }

    fun toggleFavorite(albumKey: String) = viewModelScope.launch {
        prefsStore.update { current ->
            val favorites = current.favorites.toMutableSet()
            if (!favorites.add(albumKey)) favorites.remove(albumKey)
            current.copy(favorites = favorites)
        }
    }

    fun updateSettings(block: (Prefs) -> Prefs) = viewModelScope.launch {
        var requestedTab: String? = null
        prefsStore.update { current ->
            val next = block(current)
            if (next.defaultTab != current.defaultTab) requestedTab = next.defaultTab
            next
        }
        requestedTab?.let { name ->
            runCatching { tab.value = LibraryTab.valueOf(name) }
        }
    }

    fun playTracks(tracks: List<TrackEntity>, startId: String) {
        val index = tracks.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        val current = player.state.value
        if (current.trackId == startId && current.playing) {
            player.toggle()
            persistPlaybackState()
            return
        }
        player.play(tracks, index)
        persistQueueSoon(tracks, index)
    }

    fun shuffleAll(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val shuffled = tracks.shuffled()
        player.setShuffleEnabled(true)
        player.play(shuffled, 0)
        toast("Shuffling ${shuffled.size} ${if (shuffled.size == 1) "song" else "songs"}.")
        persistQueueSoon(shuffled, 0)
    }

    private fun persistQueueSoon(tracks: List<TrackEntity>, index: Int) = viewModelScope.launch {
        prefsStore.update {
            it.copy(
                queue = tracks.map(TrackEntity::id),
                currentId = tracks.getOrNull(index)?.id,
                position = 0L,
            )
        }
    }

    fun persistPlaybackState() = viewModelScope.launch {
        persistSnapshot(player.state.value)
    }

    private suspend fun persistSnapshot(
        snapshot: PlayerSnapshot,
        progress: PlaybackProgress = player.progress.value,
    ) {
        val queueIds = player.queueIds()
        prefsStore.update { current ->
            current.copy(
                queue = queueIds,
                currentId = snapshot.trackId,
                position = progress.elapsedMs.coerceAtLeast(0L),
                volume = if (snapshot.muted) current.volume else snapshot.volume,
                muted = snapshot.muted,
                shuffle = snapshot.shuffle,
                repeat = snapshot.repeat,
            )
        }
    }

    // ---- library management -------------------------------------------------

    /** Atomically consumes the one-time first-launch permission prompt. */
    suspend fun consumeStartupAudioPermissionPrompt(): Boolean {
        var shouldPrompt = false
        prefsStore.update { current ->
            if (current.audioPermissionPrompted) current
            else {
                shouldPrompt = true
                current.copy(audioPermissionPrompted = true)
            }
        }
        return shouldPrompt
    }

    suspend fun markAudioPermissionPrompted() {
        prefsStore.update { it.copy(audioPermissionPrompted = true) }
    }

    /** Re-reads Android's live permission state; never treats DataStore as a grant. */
    fun refreshMediaStorePermission(scanIfAlreadyGranted: Boolean = false) {
        val granted = repo.mediaStorePermissionGranted
        val changed = mediaStorePermission.value != granted
        if (changed) mediaStorePermission.value = granted
        if (granted && (changed || scanIfAlreadyGranted)) rescanDeviceMusic(waitForScan = true)
    }

    fun connectFolder(uri: Uri) = viewModelScope.launch { repo.connectFolder(uri) }
    fun rescan() = viewModelScope.launch {
        prefsStore.flow.first().treeUri?.let { repo.rescan(Uri.parse(it)) }
    }
    fun disconnectFolder() = viewModelScope.launch {
        if (player.containsFolderTracks()) {
            player.clearQueue()
            prefsStore.update { it.copy(queue = emptyList(), currentId = null, position = 0L) }
        }
        repo.disconnectFolder(prefsStore.flow.first().treeUri)
    }
    fun ingestFiles(uris: List<Uri>) = viewModelScope.launch { repo.ingestFiles(uris) }
    fun rescanDeviceMusic(waitForScan: Boolean = false) = viewModelScope.launch {
        repo.rescanMediaStore(waitForScan = waitForScan)
    }
    fun clearArtwork() = viewModelScope.launch { repo.clearArtwork() }
    fun eraseEverything() = viewModelScope.launch {
        player.clearQueue()
        repo.eraseEverything(prefsStore.flow.first().treeUri)
    }

    fun savePlaylist(id: String?, name: String, trackIds: List<String>) = viewModelScope.launch {
        val serializedIds = withContext(Dispatchers.Default) { trackIds.distinct().joinToString("\n") }
        repo.savePlaylist(
            PlaylistEntity(
                id = id ?: "pl-${UUID.randomUUID()}",
                name = name,
                trackIds = serializedIds,
                custom = true,
            ),
        )
        if (id == null) toast("\"$name\" was added to your playlists.")
    }

    fun addToPlaylist(playlist: PlaylistEntity, trackId: String) = viewModelScope.launch {
        val updatedIds = withContext(Dispatchers.Default) {
            val ids = playlist.trackIds.split('\n').filter { it.isNotBlank() }
            if (trackId in ids) null else (ids + trackId).joinToString("\n")
        } ?: return@launch
        repo.savePlaylist(playlist.copy(trackIds = updatedIds))
        toast("Added to ${playlist.name}.")
    }

    fun deletePlaylist(id: String) = viewModelScope.launch {
        repo.deletePlaylist(id)
        toast("Playlist deleted.")
    }

    suspend fun hasFolderAccess(treeUri: String?) = repo.hasFolderAccess(treeUri)

    // ---- queue --------------------------------------------------------------

    fun queueTracks(): List<TrackEntity> = player.queueTracks()
    fun jumpToQueued(track: TrackEntity) {
        if (player.jumpTo(track.id)) persistPlaybackState()
    }
    fun removeFromQueue(track: TrackEntity) {
        if (player.removeFromQueue(track.id)) persistPlaybackState()
    }
    fun clearQueue() {
        player.clearQueue()
        viewModelScope.launch {
            prefsStore.update { it.copy(queue = emptyList(), currentId = null, position = 0L) }
        }
        toast("Queue cleared.")
    }

    fun enqueue(tracks: List<TrackEntity>, label: String) {
        if (tracks.isEmpty()) return
        if (!player.enqueue(tracks)) {
            toast("The playback service is reconnecting. Try adding the songs again.")
            return
        }
        if (player.state.value.hasSource && !player.isQueueRestorationPending) persistPlaybackState()
        toast("Added $label to the queue.")
    }

    // ---- playback ------------------------------------------------------------

    override fun onCleared() {
        player.release()
        CoroutineScope(Dispatchers.IO).launch { searchIndex.close() }
        super.onCleared()
    }
}

fun PlaylistEntity.ids(): List<String> = trackIds.split('\n').filter { it.isNotBlank() }
