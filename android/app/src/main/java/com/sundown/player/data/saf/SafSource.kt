package com.sundown.player.data.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import com.sundown.player.nativecore.SundownCore

/** SAF-backed source. Access is only through grants explicitly given by the user. */
class SafSource(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    data class Found(
        val id: String,
        val docUri: String,
        val path: String,
        val name: String,
        val size: Long,
        val mtime: Long,
    )

    /** Save and verify the read grant before recording this source as connected. */
    fun persist(treeUri: Uri) {
        try {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            throw IOException("Android did not retain access to the selected folder. Please choose it again.", error)
        } catch (error: UnsupportedOperationException) {
            throw IOException("This storage provider cannot keep folder access. Choose another folder or add files individually.", error)
        }
        if (!hasAccess(treeUri)) {
            throw IOException("Folder access was not saved by Android. Please choose the folder again.")
        }
    }

    fun release(treeUri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun hasAccess(treeUri: Uri): Boolean = resolver.persistedUriPermissions.any {
        it.uri == treeUri && it.isReadPermission
    }

    fun displayName(treeUri: Uri): String = runCatching {
        DocumentsContract.getTreeDocumentId(treeUri)
            .substringAfterLast('/')
            .substringAfterLast(':')
            .ifBlank { "Music" }
    }.getOrDefault("Music")

    /**
     * Breadth-first walk of the selected tree. Provider errors are fatal for
     * this pass: callers must not interpret a partial walk as deletions.
     */
    suspend fun walk(treeUri: Uri, onProgress: (Int) -> Unit): List<Found> = withContext(Dispatchers.IO) {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (error: Exception) {
            throw IOException("Android could not open the selected music folder.", error)
        }
        val sourceKey = stableKey(treeUri.toString()).take(24)
        val coroutineContext = currentCoroutineContext()
        val out = mutableListOf<Found>()
        val queue = ArrayDeque<Pair<String, String>>().apply { addLast(rootId to "") }
        val visited = hashSetOf(rootId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parentId, prefix) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            try {
                val cursor = resolver.query(childrenUri, projection, null, null, null)
                    ?: throw IOException("The storage provider returned no listing for $prefix. Existing library entries were kept.")
                cursor.use { rows ->
                    val idColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedColumn = rows.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                        throw IOException("The storage provider returned an incomplete folder listing. Existing library entries were kept.")
                    }
                    while (rows.moveToNext()) {
                        coroutineContext.ensureActive()
                        val documentId = rows.getString(idColumn)?.takeIf(String::isNotBlank) ?: continue
                        val name = rows.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                        val mime = rows.getString(mimeColumn).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (visited.add(documentId)) queue.addLast(documentId to "$prefix$name/")
                        } else if (isAudio(name, mime)) {
                            val relative = "$prefix$name"
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            out += Found(
                                id = "fs:$sourceKey:$relative",
                                docUri = documentUri.toString(),
                                path = relative,
                                name = name,
                                size = rows.longOrZero(sizeColumn),
                                mtime = rows.longOrZero(modifiedColumn),
                            )
                            if (out.size % 25 == 0) onProgress(out.size)
                        }
                    }
                }
            } catch (error: SecurityException) {
                throw IOException("Folder access was revoked while reading $prefix. Existing library entries were kept.", error)
            } catch (error: IOException) {
                throw error
            } catch (error: Exception) {
                throw IOException("Could not finish reading $prefix. Existing library entries were kept.", error)
            }
        }
        onProgress(out.size)
        out
    }

    /** Bounded head/tail reads for tags; platform metadata is the fallback. */
    suspend fun readEnds(uri: Uri, headBytes: Int, tailBytes: Int): Pair<ByteArray, ByteArray> =
        withContext(Dispatchers.IO) {
            val descriptor = resolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Android could not open this audio file.")
            val size = descriptor.statSize
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                val headLimit = if (size >= 0L) minOf(size, headBytes.toLong()).toInt() else headBytes
                val head = readUpTo(stream, headLimit)
                if (tailBytes <= 0 || size <= head.size.toLong()) return@use head to ByteArray(0)

                val tailStart = (size - tailBytes).coerceAtLeast(head.size.toLong())
                if (!skipFully(stream, tailStart - head.size)) return@use head to ByteArray(0)
                val tailLength = (size - tailStart).coerceAtMost(tailBytes.toLong()).toInt()
                head to readUpTo(stream, tailLength)
            }
        }

    private fun Cursor.longOrZero(column: Int): Long =
        if (column < 0 || isNull(column)) 0L else runCatching { getLong(column) }.getOrDefault(0L)

    private fun readUpTo(stream: java.io.InputStream, limit: Int): ByteArray {
        if (limit <= 0) return ByteArray(0)
        val bytes = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val count = stream.read(bytes, filled, limit - filled)
            if (count < 0) break
            if (count == 0) continue
            filled += count
        }
        return if (filled == limit) bytes else bytes.copyOf(filled)
    }

    private fun skipFully(stream: java.io.InputStream, bytes: Long): Boolean {
        var remaining = bytes
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val count = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (count < 0) return false
                remaining -= count
            }
        }
        return true
    }

    companion object {
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "weba", "webm")

        fun stableKey(value: String): String = SundownCore.blake3Key(value)

        fun isAudio(name: String, mime: String): Boolean {
            if (mime.startsWith("audio/", ignoreCase = true)) return true
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in AUDIO_EXT
        }
    }
}
