package com.sundown.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.sundown.player.nativecore.SundownCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hybrid native metadata reader: Android extractor for real duration/compatibility, Rust for deeper tags/art. */
class MetadataExtractor(private val context: Context) {
    data class Result(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val trackNo: Int,
        val discNo: Int,
        val year: Int,
        val durationSec: Int,
        val picture: ByteArray?,
    )

    /**
     * Rebuilt High-Performance Metadata Reader.
     * Uses Lofty (Rust) for deep parsing and Android for duration/platform fallback.
     */
    suspend fun read(uri: Uri, fallbackTitle: String): Result = withContext(Dispatchers.IO) {
        // Platform fallback for duration (MediaMetadataRetriever is fastest for duration)
        val duration = readPlatformDuration(uri)
        
        // Full deep parse using new Lofty-powered Rust core
        val bytes = readFullFile(uri) ?: return@withContext empty(fallbackTitle)
        val result = com.sundown.player.nativecore.SundownCore.parseMetadata(bytes, fallbackTitle)
        
        if (result == null) return@withContext empty(fallbackTitle)
        
        val tags = result.metadata
        Result(
            title = tags.title,
            artist = tags.artist,
            album = tags.album,
            albumArtist = tags.albumArtist,
            genre = tags.genre,
            trackNo = tags.trackNo,
            discNo = tags.discNo,
            year = tags.year,
            durationSec = duration ?: (tags.durationMs / 1000),
            picture = result.picture
        )
    }

    private fun readPlatformDuration(uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L
        } catch (_: Exception) { null } finally { retriever.release() }
    }.toInt()

    private fun readFullFile(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    private fun readPlatform(uri: Uri, fallbackTitle: String): Result? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Result(
                title = retriever.str(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { fallbackTitle },
                artist = retriever.str(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.str(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.str(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = retriever.str(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNo = retriever.num(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                discNo = retriever.num(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                year = retriever.num(MediaMetadataRetriever.METADATA_KEY_YEAR).takeIf { it in 1000..3000 }
                    ?: retriever.num(MediaMetadataRetriever.METADATA_KEY_DATE).takeIf { it in 1000..3000 } ?: 0,
                durationSec = (retriever.numLong(MediaMetadataRetriever.METADATA_KEY_DURATION) / 1_000L)
                    .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                // embeddedPicture eagerly materializes the entire blob before a size check can run.
                // Artwork is extracted by Rust from the bounded head/tail slices below instead.
                picture = null,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readEnds(uri: Uri): Pair<ByteArray, ByteArray> = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            if (pfd == null) return@use ByteArray(0) to ByteArray(0)
            val total = pfd.statSize
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                val headSize = if (total in 1..HEAD.toLong()) total.toInt() else HEAD
                val head = ByteArray(headSize)
                var filled = 0
                while (filled < headSize) {
                    val n = stream.read(head, filled, headSize - filled)
                    if (n <= 0) break
                    filled += n
                }
                val headSlice = if (filled == headSize) head else head.copyOf(filled)
                if (total <= headSlice.size.toLong() || TAIL <= 0) return@use headSlice to ByteArray(0)
                val tailStart = (total - TAIL).coerceAtLeast(headSlice.size.toLong())
                var skipped = headSlice.size.toLong()
                val scratch = ByteArray(8 * 1024)
                while (skipped < tailStart) {
                    val n = stream.skip(tailStart - skipped)
                    if (n > 0) skipped += n else {
                        val r = stream.read(scratch, 0, minOf(scratch.size.toLong(), tailStart - skipped).toInt())
                        if (r < 0) break
                        skipped += r
                    }
                }
                val tailLen = (total - tailStart).coerceAtMost(TAIL.toLong()).toInt()
                val tail = ByteArray(tailLen)
                var got = 0
                while (got < tailLen) {
                    val n = stream.read(tail, got, tailLen - got)
                    if (n <= 0) break
                    got += n
                }
                headSlice to if (got == tailLen) tail else tail.copyOf(got)
            }
        }
    }.getOrDefault(ByteArray(0) to ByteArray(0))

    private fun empty(title: String) = Result(title, "", "", "", "", 0, 0, 0, 0, null)

    private fun MediaMetadataRetriever.str(key: Int): String = runCatching { extractMetadata(key).orEmpty().trim() }.getOrDefault("")
    private fun MediaMetadataRetriever.num(key: Int): Int = extractMetadata(key)?.substringBefore('/')?.toIntOrNull() ?: 0
    private fun MediaMetadataRetriever.numLong(key: Int): Long = extractMetadata(key)?.toLongOrNull() ?: 0L

    private companion object {
        const val HEAD = 1 shl 21
        const val TAIL = 1 shl 18
    }
}
