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

    suspend fun read(uri: Uri, fallbackTitle: String): Result = withContext(Dispatchers.IO) {
        val platform = readPlatform(uri, fallbackTitle)
        val needsRust = !SundownCore.available || platform == null ||
            platform.artist.isBlank() || platform.album.isBlank() ||
            platform.albumArtist.isBlank() || platform.genre.isBlank() ||
            platform.trackNo == 0 || platform.discNo == 0 || platform.picture == null
        if (!needsRust || !SundownCore.available) return@withContext platform ?: empty(fallbackTitle)

        val (head, tail) = readEnds(uri)
        if (head.isEmpty()) return@withContext platform ?: empty(fallbackTitle)
        val parsed = SundownCore.parseTags(head, tail, fallbackTitle)
        val tags = parsed.tags
        Result(
            title = platform?.title?.takeIf { it.isNotBlank() } ?: tags.title.ifBlank { fallbackTitle },
            artist = platform?.artist?.ifBlank { tags.artist } ?: tags.artist,
            album = platform?.album?.ifBlank { tags.album } ?: tags.album,
            albumArtist = platform?.albumArtist?.ifBlank { tags.albumArtist } ?: tags.albumArtist,
            genre = platform?.genre?.ifBlank { tags.genre } ?: tags.genre,
            trackNo = platform?.trackNo?.takeIf { it > 0 } ?: tags.trackNo,
            discNo = platform?.discNo?.takeIf { it > 0 } ?: tags.discNo,
            year = platform?.year?.takeIf { it > 0 } ?: tags.year,
            durationSec = platform?.durationSec ?: 0,
            picture = platform?.picture ?: parsed.picture,
        )
    }

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
                picture = runCatching { retriever.embeddedPicture?.takeIf { it.isNotEmpty() && it.size <= MAX_EMBEDDED_ART_BYTES } }.getOrNull(),
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
    private fun MediaMetadataRetriever.num(key: Int): Int = extractMetadata(key)?.substringBefore('/').toIntOrNull() ?: 0
    private fun MediaMetadataRetriever.numLong(key: Int): Long = extractMetadata(key)?.toLongOrNull() ?: 0L

    private companion object {
        const val HEAD = 1 shl 21
        const val TAIL = 1 shl 18
        const val MAX_EMBEDDED_ART_BYTES = 20 * 1024 * 1024
    }
}
