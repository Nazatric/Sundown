package com.sundown.player.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.sundown.player.data.saf.SafSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                val mediaId = rows.getLong(idColumn)
                val name = if (nameColumn >= 0) rows.getString(nameColumn).orEmpty() else "audio-$mediaId"
                val mime = if (mimeColumn >= 0) rows.getString(mimeColumn).orEmpty() else ""
                if (!SafSource.isAudio(name, mime)) continue
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(mediaId.toString()).build()
                out += Found(
                    id = "ms:$mediaId",
                    docUri = uri.toString(),
                    path = name,
                    name = name,
                    size = if (sizeColumn >= 0 && !rows.isNull(sizeColumn)) rows.getLong(sizeColumn) else 0L,
                    mtime = if (modifiedColumn >= 0 && !rows.isNull(modifiedColumn)) rows.getLong(modifiedColumn) * 1_000L else 0L,
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
