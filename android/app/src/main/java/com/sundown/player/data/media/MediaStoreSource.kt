package com.sundown.player.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.sundown.player.data.saf.SafSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.IOException

/** Native MediaStore music source. It never writes or renames the user's files. */
class MediaStoreSource(private val context: Context) {
    data class Found(
        val id: String,
        val docUri: String,
        val path: String,
        val name: String,
        val size: Long,
        val mtime: Long,
        val sourceVersion: String?,
    )

    data class ScanSnapshot(
        /** Full rows on first scan; changed rows only when a volume has a valid watermark. */
        val changedTracks: List<Found>,
        /** ID-only snapshots detect deletions without reopening unchanged audio files. */
        val currentIds: Set<String>,
        /** Only IDs from these mounted volumes may be removed from the cached library. */
        val scannedVolumes: Set<String>,
        /** Successful version/generation checkpoints, keyed by mounted MediaStore volume name. */
        val checkpoints: Map<String, MediaStoreCheckpoint>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        val watchedUris = withContext(Dispatchers.IO) {
            buildList {
                add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { MediaStore.getExternalVolumeNames(context) }
                        .getOrDefault(emptySet())
                        .forEach { volume ->
                            runCatching { MediaStore.Audio.Media.getContentUri(volume) }
                                .getOrNull()
                                ?.let(::add)
                        }
                }
            }.distinct()
        }
        var registered = false
        watchedUris.forEach { uri ->
            runCatching { context.contentResolver.registerContentObserver(uri, true, observer) }
                .onSuccess { registered = true }
        }
        if (!registered) {
            close(IOException("Android could not observe changes to the device music library."))
            return@callbackFlow
        }
        awaitClose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }.conflate()

    /**
     * API 30+ uses a generation watermark per mounted volume for changed rows
     * and an ID-only snapshot for deletions. Older Android/provider combinations
     * fall back to a full metadata query, but unchanged audio is never reopened
     * unless its source fingerprint changes.
     */
    suspend fun scan(
        afterCheckpoints: Map<String, MediaStoreCheckpoint>,
        onProgress: (Int) -> Unit,
    ): ScanSnapshot = withContext(Dispatchers.IO) {
        if (!hasReadPermission(context)) throw IOException("Music library permission is not granted.")
        val volumes = externalVolumes()
        val changed = ArrayList<Found>()
        val currentIds = LinkedHashSet<String>()
        val checkpoints = LinkedHashMap<String, MediaStoreCheckpoint>()
        var changedRows = 0

        volumes.forEach { volume ->
            val version = mediaStoreVersion(volume)
            val upperGeneration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { MediaStore.getGeneration(context, volume) }.getOrNull()
            } else {
                null
            }
            val range = mediaStoreGenerationRange(afterCheckpoints[volume], upperGeneration, version)
            val rows = queryTracks(volume, range?.after, range?.through, version) { count -> onProgress(changedRows + count) }
            changed += rows
            changedRows += rows.size

            val ids = queryCurrentIds(volume) { count -> onProgress(currentIds.size + count) }
            currentIds += ids

            if (version != null && upperGeneration != null) {
                if (mediaStoreVersion(volume) != version) {
                    throw IOException("The MediaStore index changed during scanning. Please retry.")
                }
                checkpoints[volume] = MediaStoreCheckpoint(version, upperGeneration)
            }
        }
        onProgress(currentIds.size)
        ScanSnapshot(changed, currentIds, volumes.toSet(), checkpoints)
    }

    private fun externalVolumes(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return listOf(LEGACY_EXTERNAL_VOLUME)
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context).toList().sorted() }
            .getOrElse { throw IOException("Android could not list mounted music-storage volumes.", it) }
        if (volumes.isEmpty()) throw IOException("No external music-storage volume is currently available.")
        return volumes
    }

    private fun mediaStoreVersion(volume: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getVersion(context, volume) }.getOrNull()?.takeIf(String::isNotBlank)
        } else {
            null
        }

    private fun audioCollection(volume: String): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(volume)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    private suspend fun queryTracks(
        volume: String,
        afterGeneration: Long?,
        throughGeneration: Long?,
        sourceVersion: String?,
        onProgress: (Int) -> Unit,
    ): List<Found> {
        val resolver = context.contentResolver
        val collection = audioCollection(volume)
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.IS_MUSIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }.toTypedArray()
        val selection: String
        val selectionArgs: Array<String>?
        if (afterGeneration != null && throughGeneration != null) {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ? AND " +
                "${MediaStore.MediaColumns.GENERATION_MODIFIED} <= ?"
            selectionArgs = arrayOf(afterGeneration.toString(), throughGeneration.toString())
        } else {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            selectionArgs = null
        }
        val out = ArrayList<Found>()
        val coroutineContext = currentCoroutineContext()
        val cursor = resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        ) ?: throw IOException("Android could not read the device music library.")

        cursor.use { rows ->
            val idColumn = rows.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = rows.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeColumn = rows.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val sizeColumn = rows.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val modifiedColumn = rows.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val relativePathColumn = rows.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val generationColumn = rows.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                val mediaId = rows.getLong(idColumn)
                val name = if (nameColumn >= 0) rows.getString(nameColumn).orEmpty() else "audio-$mediaId"
                val mime = if (mimeColumn >= 0) rows.getString(mimeColumn).orEmpty() else ""
                if (!SafSource.isAudio(name, mime)) continue
                val uri = collection.buildUpon().appendPath(mediaId.toString()).build()
                val relativePath = if (relativePathColumn >= 0) rows.getString(relativePathColumn).orEmpty().trim('/') else ""
                val path = if (relativePath.isBlank()) name else "$relativePath/$name"
                val generation = if (generationColumn >= 0 && !rows.isNull(generationColumn)) rows.getLong(generationColumn) else 0L
                val modified = if (modifiedColumn >= 0 && !rows.isNull(modifiedColumn)) rows.getLong(modifiedColumn) * 1_000L else 0L
                out += Found(
                    id = itemId(volume, mediaId),
                    docUri = uri.toString(),
                    path = path,
                    name = name,
                    size = if (sizeColumn >= 0 && !rows.isNull(sizeColumn)) rows.getLong(sizeColumn) else 0L,
                    // Generation changes even if size/date_modified do not; the
                    // separate version fingerprint forces reparse after provider rebuilds.
                    mtime = generation.takeIf { it > 0L } ?: modified,
                    sourceVersion = sourceVersion,
                )
                if (out.size % PROGRESS_INTERVAL == 0) onProgress(out.size)
            }
        }
        return out
    }

    private suspend fun queryCurrentIds(volume: String, onProgress: (Int) -> Unit): Set<String> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        val cursor = context.contentResolver.query(
            audioCollection(volume),
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        ) ?: throw IOException("Android could not verify the current device music IDs.")
        val ids = LinkedHashSet<String>()
        var scannedRows = 0
        val coroutineContext = currentCoroutineContext()
        cursor.use { rows ->
            val idColumn = rows.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = rows.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeColumn = rows.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                scannedRows += 1
                val mediaId = rows.getLong(idColumn)
                val name = if (nameColumn >= 0) rows.getString(nameColumn).orEmpty() else "audio-$mediaId"
                val mime = if (mimeColumn >= 0) rows.getString(mimeColumn).orEmpty() else ""
                if (SafSource.isAudio(name, mime)) ids += itemId(volume, mediaId)
                if (scannedRows % PROGRESS_INTERVAL == 0) onProgress(ids.size)
            }
        }
        return ids
    }

    private fun itemId(volume: String, mediaId: Long): String = when (volume) {
        LEGACY_EXTERNAL_VOLUME, PRIMARY_EXTERNAL_VOLUME -> "ms:$mediaId"
        else -> "ms:$volume:$mediaId"
    }

    companion object {
        private const val LEGACY_EXTERNAL_VOLUME = "external"
        private const val PRIMARY_EXTERNAL_VOLUME = "external_primary"
        private const val PROGRESS_INTERVAL = 50

        fun hasReadPermission(context: Context): Boolean {
            val permission = if (Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        fun permissionForCurrentApi(): String = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}
