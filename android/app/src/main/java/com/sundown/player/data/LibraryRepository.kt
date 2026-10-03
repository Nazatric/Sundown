package com.sundown.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.sundown.player.data.db.PlaylistEntity
import com.sundown.player.data.db.SundownDatabase
import com.sundown.player.data.db.TrackEntity
import com.sundown.player.data.prefs.Prefs
import com.sundown.player.data.prefs.SundownPrefs
import com.sundown.player.data.saf.SafSource
import com.sundown.player.data.media.MediaStoreSource
import com.sundown.player.nativecore.SundownCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ScanProgress(
    val phase: String,
    val done: Int,
    val total: Int,
    val current: String,
)

/** SAF scanning, incremental fingerprints, Room persistence and user playlists. */
class LibraryRepository(
    private val context: Context,
    private val prefs: SundownPrefs,
    private val artwork: ArtworkStore,
) {
    private val dao = SundownDatabase.get(context).libraryDao()
    private val saf = SafSource(context)
    private val mediaStore = MediaStoreSource(context)
    private val extractor = MetadataExtractor(context)
    private val scanLock = Mutex()
    // Large image decodes can dominate heap usage, so never decode multiple covers concurrently.
    private val artworkDecode = Semaphore(1)

    val tracks: Flow<List<TrackEntity>> = dao.observeTracks()

    val mediaStorePermissionGranted: Boolean get() = MediaStoreSource.hasReadPermission(context)
    val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()

    private val _progress = MutableStateFlow<ScanProgress?>(null)
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun hasFolderAccess(treeUri: String?): Boolean =
        treeUri != null && runCatching { saf.hasAccess(Uri.parse(treeUri)) }.getOrDefault(false)

    suspend fun connectFolder(treeUri: Uri) {
        val previous = prefs.flow.first()
        try {
            // Persist first; never replace the working source with an ephemeral
            // grant and then discover that it cannot survive an app restart.
            saf.persist(treeUri)
            if (rescan(treeUri)) {
                prefs.update { it.copy(treeUri = treeUri.toString(), treeName = saf.displayName(treeUri)) }
            } else if (previous.treeUri != treeUri.toString()) {
                saf.release(treeUri)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (previous.treeUri != treeUri.toString()) saf.release(treeUri)
            _messages.send(error.message ?: "Folder access could not be saved.")
        }
    }

    suspend fun disconnectFolder(treeUri: String?) {
        if (!scanLock.tryLock()) {
            _messages.send("A scan is in progress. Try disconnecting the folder again when it finishes.")
            return
        }
        try {
            treeUri?.let { saf.release(Uri.parse(it)) }
            dao.deleteSource(SOURCE_FOLDER)
            prefs.update { it.copy(treeUri = null, treeName = null) }
            _messages.send("Folder disconnected. Individually added files and playlists were kept.")
        } finally {
            scanLock.unlock()
        }
    }

    /**
     * A scan only prunes absent rows after the provider walk and every changed
     * file read succeeded. Partial provider listings can never become deletes.
     */
    suspend fun rescan(treeUri: Uri): Boolean {
        if (!scanLock.tryLock()) return false
        var completed = false
        try {
            _progress.value = ScanProgress("reading", 0, 0, saf.displayName(treeUri))
            val found = saf.walk(treeUri) { count ->
                _progress.value = ScanProgress("reading", count, 0, saf.displayName(treeUri))
            }

            val known = dao.fingerprints(SOURCE_FOLDER).associateBy { it.id }
            val toParse = found.filter { file ->
                val previous = known[file.id]
                previous == null ||
                    previous.size != file.size ||
                    previous.mtime != file.mtime ||
                    (previous.artId != null && !artwork.has(previous.artId))
            }
            val parseResult = parseAll(toParse)
            val seen = found.mapTo(HashSet()) { it.id }
            val removed = known.keys.filter { it !in seen }

            if (parseResult.failed == 0) {
                if (removed.isNotEmpty()) dao.deleteIds(removed)
                completed = true
            }

            _progress.value = ScanProgress("done", toParse.size, toParse.size, "")
            val summary = when {
                parseResult.failed > 0 ->
                    "Could not read ${parseResult.failed} ${if (parseResult.failed == 1) "file" else "files"}; existing library entries were kept."
                toParse.isEmpty() && removed.isEmpty() ->
                    "Library is up to date with ${found.size} songs."
                else ->
                    "Library updated: ${found.size} songs, ${parseResult.parsed} new or changed, ${removed.size} removed."
            }
            _messages.send(summary)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _messages.send(error.message ?: "The folder could not be scanned. Existing library entries were kept.")
        } finally {
            _progress.value = null
            scanLock.unlock()
        }
        return completed
    }

    /** Incrementally indexes the real Android music library without touching user files. */
    suspend fun rescanMediaStore(): Boolean {
        if (!scanLock.tryLock()) return false
        var completed = false
        try {
            _progress.value = ScanProgress("reading", 0, 0, "Device Music")
            val found = mediaStore.scan { count ->
                _progress.value = ScanProgress("reading", count, 0, "Device Music")
            }.map { f ->
                SafSource.Found(
                    id = f.id,
                    docUri = f.docUri,
                    path = f.path,
                    name = f.name,
                    size = f.size,
                    mtime = f.mtime,
                )
            }
            val known = dao.fingerprints(SOURCE_MEDIA).associateBy { it.id }
            val toParse = found.filter { file ->
                val previous = known[file.id]
                previous == null || previous.size != file.size || previous.mtime != file.mtime ||
                    (previous.artId != null && !artwork.has(previous.artId))
            }
            val result = parseAll(toParse, source = SOURCE_MEDIA)
            val seen = found.mapTo(HashSet()) { it.id }
            val removed = known.keys.filter { it !in seen }
            if (result.failed == 0) {
                if (removed.isNotEmpty()) dao.deleteIds(removed)
                completed = true
            }
            _messages.send(
                if (result.failed > 0) {
                    "Could not read ${result.failed} device songs; existing entries were kept."
                } else if (toParse.isEmpty() && removed.isEmpty()) {
                    "Device music is up to date with ${found.size} songs."
                } else {
                    "Device music updated: ${found.size} songs, ${result.parsed} new or changed, ${removed.size} removed."
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _messages.send(error.message ?: "The device music library could not be scanned.")
        } finally {
            _progress.value = null
            scanLock.unlock()
        }
        return completed
    }

    /** Individually selected `ACTION_OPEN_DOCUMENT` files retain their URI grant. */
    suspend fun ingestFiles(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _messages.send("No audio files were selected.")
            return
        }
        if (!scanLock.tryLock()) return
        try {
            val found = uris.mapNotNull { uri ->
                val name = queryName(uri) ?: return@mapNotNull null
                if (!SafSource.isAudio(name, context.contentResolver.getType(uri).orEmpty())) return@mapNotNull null
                SafSource.Found(
                    id = "fl:${SafSource.stableKey(uri.toString())}",
                    docUri = uri.toString(),
                    path = name,
                    name = name,
                    size = queryLong(uri, OpenableColumns.SIZE),
                    mtime = 0,
                )
            }
            val result = parseAll(found, source = SOURCE_FILE)
            when {
                found.isEmpty() -> _messages.send("No supported audio files were selected.")
                result.failed > 0 -> _messages.send("${result.parsed} songs added; ${result.failed} files could not be read.")
                else -> _messages.send("${result.parsed} ${if (result.parsed == 1) "song" else "songs"} added.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _messages.send(error.message ?: "The selected files could not be read.")
        } finally {
            _progress.value = null
            scanLock.unlock()
        }
    }

    private data class ParseResult(val parsed: Int, val failed: Int)

    /** Bounded worker pool; Room is flushed in batches as parsing completes. */
    private suspend fun parseAll(files: List<SafSource.Found>, source: String = SOURCE_FOLDER): ParseResult = coroutineScope {
        if (files.isEmpty()) return@coroutineScope ParseResult(0, 0)
        val parallelism = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 4)
        val dispatcher = Dispatchers.IO.limitedParallelism(parallelism)
        val pending = java.util.Collections.synchronizedList(mutableListOf<TrackEntity>())
        var done = 0
        var failed = 0
        var parsedCount = 0
        var lastProgressDone = 0
        var lastProgressNanos = System.nanoTime()

        _progress.value = ScanProgress("parsing", 0, files.size, "")
        files.chunked(BATCH).forEach { chunk ->
            chunk.map { file ->
                async(dispatcher) {
                    val entity = try {
                        parseOne(file, source)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not parse ${file.path}", error)
                        null
                    }
                    synchronized(pending) {
                        if (entity != null) pending += entity else failed += 1
                        done += 1
                        val now = System.nanoTime()
                        if (done == files.size || done - lastProgressDone >= 16 || now - lastProgressNanos >= 100_000_000L) {
                            _progress.value = ScanProgress("parsing", done, files.size, file.path)
                            lastProgressDone = done
                            lastProgressNanos = now
                        }
                    }
                }
            }.awaitAll()

            val flush = synchronized(pending) { pending.toList().also { pending.clear() } }
            if (flush.isNotEmpty()) {
                dao.upsertAll(flush)
                dao.fillMissingArtwork(flush.map(TrackEntity::albumKey).distinct())
                parsedCount += flush.size
            }
        }
        ParseResult(parsedCount, failed)
    }

    private suspend fun parseOne(file: SafSource.Found, source: String): TrackEntity {
        val uri = Uri.parse(file.docUri)
        val fallbackTitle = file.name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { file.name }
        val tags = extractor.read(uri, fallbackTitle)

        val artist = tags.artist.ifBlank { "Unknown Artist" }
        val album = tags.album.ifBlank { "Unknown Album" }
        val albumKey = SundownCore.albumKey(tags.albumArtist, artist, album)
        val candidate = "art_${SundownCore.blake3Key(albumKey).take(32)}"
        val picture = tags.picture
        val artId = when {
            artwork.has(candidate) -> candidate
            picture != null -> {
                val previews = artworkDecode.withPermit { SundownCore.makeArtPreviews(picture) }
                if (previews != null && (artwork.write(candidate, previews.large, previews.small) || artwork.has(candidate))) candidate else null
            }
            else -> null
        }

        return TrackEntity(
            id = file.id,
            docUri = file.docUri,
            path = file.path,
            name = file.name,
            size = file.size,
            mtime = file.mtime,
            title = tags.title.ifBlank { fallbackTitle },
            artist = artist,
            album = album,
            albumArtist = tags.albumArtist,
            genre = tags.genre,
            trackNo = tags.trackNo,
            discNo = tags.discNo,
            year = tags.year,
            duration = tags.durationSec,
            artId = artId,
            albumKey = albumKey,
            artistKey = SundownCore.artistKey(tags.albumArtist, artist),
            source = source,
            addedAt = System.currentTimeMillis(),
        )
    }

    private fun queryName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun queryLong(uri: Uri, columnName: String): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(columnName), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(columnName)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    suspend fun updateDuration(id: String, seconds: Int) = dao.updateDuration(id, seconds)
    suspend fun savePlaylist(playlist: PlaylistEntity) = dao.upsertPlaylist(playlist)
    suspend fun deletePlaylist(id: String) = dao.deletePlaylist(id)

    suspend fun clearArtwork() {
        if (!scanLock.tryLock()) {
            _messages.send("A scan is in progress. Try clearing artwork again when it finishes.")
            return
        }
        try {
            artwork.clear()
            // Keep art IDs: the next rescan detects missing cache files and
            // reparses only tracks whose artwork was actually evicted.
            _messages.send("Artwork cache cleared. Rescan the folder to rebuild covers.")
        } finally {
            scanLock.unlock()
        }
    }

    suspend fun eraseEverything(currentTree: String?) {
        if (!scanLock.tryLock()) {
            _messages.send("A scan is in progress. Try erasing library data again when it finishes.")
            return
        }
        try {
            currentTree?.let { saf.release(Uri.parse(it)) }
            context.contentResolver.persistedUriPermissions.toList().forEach { grant ->
                val flags = (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                runCatching { context.contentResolver.releasePersistableUriPermission(grant.uri, flags) }
            }
            dao.clearTracks()
            dao.clearPlaylists()
            artwork.clear()
            prefs.update { Prefs() }
            _messages.send("Library data erased. Choose your music folder to start over.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _messages.send(error.message ?: "Library data could not be erased completely.")
        } finally {
            scanLock.unlock()
        }
    }

    companion object {
        const val SOURCE_FOLDER = "folder"
        const val SOURCE_FILE = "file"
        const val SOURCE_MEDIA = "media"
        private const val HEAD_BYTES = 1 shl 21
        private const val TAIL_BYTES = 1 shl 18
        private const val BATCH = 24
        private const val TAG = "SundownLibrary"
    }
}
