package com.sundown.player.nativecore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import uniffi.sundown_core.albumKeyOf as rustAlbumKeyOf
import uniffi.sundown_core.blake3Key as rustBlake3Key
import uniffi.sundown_core.artistKeyOf as rustArtistKeyOf
import uniffi.sundown_core.makeArtPreviews as rustMakeArtPreviews
import uniffi.sundown_core.parseTags as rustParseTags
import uniffi.sundown_core.sortNameOf as rustSortNameOf
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** The Kotlin boundary for the generated UniFFI bindings and safe fallbacks. */
object SundownCore {

    data class Tags(
        val title: String,
        val artist: String,
        val album: String,
        val albumArtist: String,
        val genre: String,
        val trackNo: Int,
        val discNo: Int,
        val year: Int,
    )

    data class Parsed(val tags: Tags, val picture: ByteArray?, val pictureMime: String)
    data class Previews(val large: ByteArray, val small: ByteArray)

    @Volatile private var loaded = false

    val available: Boolean get() = loaded

    init {
        loaded = runCatching {
            // UniFFI 0.28 initializes JNA and verifies its API contract on the
            // first generated function call; it has no public ensure-initialized helper.
            rustSortNameOf("")
            true
        }.onFailure {
            Log.e(TAG, "UniFFI native library is unavailable; using platform fallbacks.", it)
        }.getOrDefault(false)
    }

    fun parseTags(head: ByteArray, tail: ByteArray, fallbackTitle: String): Parsed {
        if (loaded) {
            runCatching { nativeParse(head, tail, fallbackTitle) }
                .onSuccess { return it }
                .onFailure { Log.w(TAG, "Rust metadata parsing failed; using the platform parser.", it) }
        }
        return Parsed(Tags(fallbackTitle, "", "", "", "", 0, 0, 0), null, "")
    }

    /** 1024 px + 160 px square JPEG previews from embedded art. */
    fun makeArtPreviews(bytes: ByteArray): Previews? {
        if (loaded) {
            runCatching { rustMakeArtPreviews(bytes)?.let { Previews(it.large, it.small) } }
                .onSuccess { if (it != null) return it }
                .onFailure { Log.w(TAG, "Rust artwork decoding failed; using BitmapFactory.", it) }
        }
        return KotlinArtworkFallback.previews(bytes)
    }

    fun albumKey(albumArtist: String, artist: String, album: String): String =
        if (loaded) runCatching { rustAlbumKeyOf(albumArtist, artist, album) }.getOrElse { fallbackAlbumKey(albumArtist, artist, album) }
        else fallbackAlbumKey(albumArtist, artist, album)

    fun artistKey(albumArtist: String, artist: String): String =
        if (loaded) runCatching { rustArtistKeyOf(albumArtist, artist) }.getOrElse { fallbackArtistKey(albumArtist, artist) }
        else fallbackArtistKey(albumArtist, artist)

    fun sortName(name: String): String =
        if (loaded) runCatching { rustSortNameOf(name) }.getOrElse { fallbackSortName(name) }
        else fallbackSortName(name)

    fun blake3Key(value: String): String =
        if (loaded) runCatching { rustBlake3Key(value) }.getOrElse { sha256Fallback(value) }
        else sha256Fallback(value)

    private fun sha256Fallback(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun nativeParse(head: ByteArray, tail: ByteArray, fallback: String): Parsed {
        val parsed = rustParseTags(head, tail, fallback)
        val source = parsed.tags
        return Parsed(
            tags = Tags(
                title = source.title,
                artist = source.artist,
                album = source.album,
                albumArtist = source.albumArtist,
                genre = source.genre,
                trackNo = source.trackNo.toInt(),
                discNo = source.discNo.toInt(),
                year = source.year.toInt(),
            ),
            picture = parsed.picture,
            pictureMime = parsed.pictureMime,
        )
    }

    private fun fallbackAlbumKey(albumArtist: String, artist: String, album: String): String {
        val who = albumArtist.ifBlank { artist }.trim().lowercase()
        val what = album.ifBlank { "Unknown Album" }.trim().lowercase()
        return "$who :: $what"
    }

    private fun fallbackArtistKey(albumArtist: String, artist: String): String =
        albumArtist.ifBlank { artist }.trim().lowercase().ifBlank { "unknown artist" }

    private fun fallbackSortName(name: String) = name.trim().lowercase().removePrefix("the ")

    private const val TAG = "SundownCore"
}

/** Bounded platform fallback: center-crop and encode the same preview sizes as Rust. */
private object KotlinArtworkFallback {
    private const val LARGE = 1_024
    private const val SMALL = 160
    private const val MAX_DECODE_DIMENSION = 2_048
    private const val JPEG_QUALITY = 92
    private val JPEG_MATTE = Color.rgb(0xC5, 0xC4, 0xBD)

    fun previews(bytes: ByteArray): SundownCore.Previews? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DECODE_DIMENSION) sampleSize *= 2
        val source = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        if (square !== source) source.recycle()
        val result = SundownCore.Previews(encode(square, LARGE), encode(square, SMALL))
        square.recycle()
        result
    }.getOrNull()

    private fun encode(source: Bitmap, target: Int): ByteArray {
        val side = minOf(target, source.width, source.height)
        val scaled = Bitmap.createScaledBitmap(source, side, side, true)
        val opaque = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(JPEG_MATTE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        val out = ByteArrayOutputStream()
        opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        opaque.recycle()
        if (scaled !== source) scaled.recycle()
        return out.toByteArray()
    }
}
