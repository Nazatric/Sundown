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
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        } catch (error: SecurityException) {
            close(error)
            return@callbackFlow
        }
        awaitClose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }.conflate()

    suspend fun scan(onProgress: (Int) -> Unit): List<Found> = withContext(Dispatchers.IO) {
        if (!hasReadPermission(context)) throw IOException("Music library permission is not granted.")
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
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
        val out = ArrayList<Found>()
        val coroutineContext = currentCoroutineContext()
        val cursor = resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
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
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(mediaId.toString()).build()
                val relativePath = if (relativePathColumn >= 0) rows.getString(relativePathColumn).orEmpty().trim('/') else ""
                val path = if (relativePath.isBlank()) name else "$relativePath/$name"
                val generation = if (generationColumn >= 0 && !rows.isNull(generationColumn)) rows.getLong(generationColumn) else 0L
                val modified = if (modifiedColumn >= 0 && !rows.isNull(modifiedColumn)) rows.getLong(modifiedColumn) * 1_000L else 0L
                out += Found(
                    id = "ms:$mediaId",
                    docUri = uri.toString(),
                    path = path,
                    name = name,
                    size = if (sizeColumn >= 0 && !rows.isNull(sizeColumn)) rows.getLong(sizeColumn) else 0L,
                    // API 30's generation changes even if size/date_modified do not.
                    mtime = generation.takeIf { it > 0L } ?: modified,
                )
                if (out.size % 50 == 0) onProgress(out.size)
            }
        }
        onProgress(out.size)
        out
    }

    companion object {
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
