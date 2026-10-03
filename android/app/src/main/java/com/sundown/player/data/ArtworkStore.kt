package com.sundown.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.sundown.player.ui.components.ArtworkLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Bounded, app-private 1024px/160px artwork previews with a coalescing bitmap cache. */
class ArtworkStore(context: Context) : ArtworkLoader {

    private val dir = File(context.filesDir, "art").apply { mkdirs() }
    private val generation = AtomicLong(0L)
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val cache = object : LruCache<String, ImageBitmap>(cacheCapacityBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun has(artId: String): Boolean =
        fileForLarge(artId).let { it.isFile && it.length() > 0L } &&
            (fileForSmallWebp(artId).let { it.isFile && it.length() > 0L } ||
                fileForSmallJpeg(artId).let { it.isFile && it.length() > 0L })

    /** LRU-evicted IDs are intentionally not rebuilt during every library scan. */
    fun needsRebuild(artId: String): Boolean = !has(artId) && !evictionMarker(artId).isFile

    suspend fun write(artId: String, large: ByteArray, small: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            atomicWrite(fileForLarge(artId), large)
            val compact = compactArtwork(small)
            if (compact.extension == "webp") {
                atomicWrite(fileForSmallWebp(artId), compact.bytes)
                fileForSmallJpeg(artId).delete()
            } else {
                atomicWrite(fileForSmallJpeg(artId), compact.bytes)
                fileForSmallWebp(artId).delete()
            }
            evictionMarker(artId).delete()
            cache.remove("l:$artId")
            cache.remove("s:$artId")
            true
        } catch (_: Exception) {
            false
        }
    }

    /** High-resolution preview used for lock-screen and notification artwork. */
    fun largeBytes(artId: String): ByteArray? = runCatching {
        fileForLarge(artId).takeIf(File::isFile)?.also(::touch)?.readBytes()
    }.getOrNull()

    fun smallBytes(artId: String): ByteArray? = runCatching {
        (fileForSmallWebp(artId).takeIf(File::isFile) ?: fileForSmallJpeg(artId).takeIf(File::isFile))
            ?.also(::touch)
            ?.readBytes()
    }.getOrNull()

    /** Removes orphaned covers and caps the on-disk cache, oldest artwork first. */
    suspend fun prune(referencedArtIds: Set<String>) = withContext(Dispatchers.IO) {
        val files = dir.listFiles()?.toList().orEmpty()
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }

        val referenced = referencedArtIds.mapTo(HashSet(), ::safeId)
        files.filter { it.name.endsWith(".evicted") }.forEach { marker ->
            if (marker.name.removeSuffix(".evicted") !in referenced) marker.delete()
        }
        val groups = files.mapNotNull { file ->
            val id = cachedArtId(file) ?: return@mapNotNull null
            id to file
        }.groupBy({ it.first }, { it.second })
        groups.keys.forEach { id -> evictionMarker(id).delete() }
        var totalBytes = groups.values.sumOf { group -> group.sumOf { it.length() } }

        groups.forEach { (id, group) ->
            if (id !in referenced) {
                val groupBytes = group.sumOf { it.length() }
                group.forEach { it.delete() }
                evictionMarker(id).delete()
                removeCachedBitmaps(id)
                totalBytes -= groupBytes
            }
        }

        if (totalBytes > MAX_DISK_CACHE_BYTES) {
            groups.asSequence()
                .filter { (id, _) -> id in referenced }
                .sortedBy { (_, group) -> group.maxOfOrNull { it.lastModified() } ?: 0L }
                .forEach { (id, group) ->
                    if (totalBytes > MAX_DISK_CACHE_BYTES) {
                        val groupBytes = group.sumOf { it.length() }
                        group.forEach { it.delete() }
                        runCatching { evictionMarker(id).createNewFile() }
                        removeCachedBitmaps(id)
                        totalBytes -= groupBytes
                    }
                }
        }
    }

    override suspend fun load(artId: String, small: Boolean): ImageBitmap? {
        val cacheKey = (if (small) "s:" else "l:") + artId
        cache.get(cacheKey)?.let { return it }

        val cacheGeneration = generation.get()
        val flightKey = "$cacheGeneration:$cacheKey"
        val task = inFlight.computeIfAbsent(flightKey) {
            loadScope.async { decode(artId, small, cacheKey, cacheGeneration) }
        }
        task.invokeOnCompletion { inFlight.remove(flightKey, task) }
        return try {
            task.await()
        } finally {
            if (task.isCompleted) inFlight.remove(flightKey, task)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        // Old decodes may finish after files are deleted. The generation keeps
        // them from repopulating the just-cleared memory cache.
        generation.incrementAndGet()
        cache.evictAll()
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        Unit
    }

    private fun decode(
        artId: String,
        small: Boolean,
        cacheKey: String,
        expectedGeneration: Long,
    ): ImageBitmap? {
        cache.get(cacheKey)?.let { return it }
        val file = if (small) {
            fileForSmallWebp(artId).takeIf(File::isFile) ?: fileForSmallJpeg(artId)
        } else {
            fileForLarge(artId)
        }
        if (!file.isFile) return null
        touch(file)
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        val image = runCatching { bitmap.asImageBitmap() }.getOrNull()
        if (image == null) {
            bitmap.recycle()
            return null
        }
        if (generation.get() == expectedGeneration) cache.put(cacheKey, image)
        return image
    }

    private fun atomicWrite(destination: File, bytes: ByteArray) {
        if (destination.isFile && destination.length() > 0L) return
        destination.delete()
        val temporary = File(dir, ".${destination.name}.${Thread.currentThread().id}.${System.nanoTime()}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(destination) && !(destination.isFile && destination.length() > 0L)) {
            temporary.delete()
            throw IllegalStateException("Could not finish writing cached cover art.")
        }
        temporary.delete()
    }

    private fun cachedArtId(file: File): String? = when {
        file.name.endsWith("_lg.jpg") -> file.name.removeSuffix("_lg.jpg")
        file.name.endsWith("_sm.webp") -> file.name.removeSuffix("_sm.webp")
        file.name.endsWith("_sm.jpg") -> file.name.removeSuffix("_sm.jpg")
        else -> null
    }

    private fun safeId(artId: String): String = artId.replace(':', '_')

    private fun evictionMarker(artId: String): File = File(dir, safeId(artId) + ".evicted")

    private fun removeCachedBitmaps(artId: String) {
        cache.remove("l:$artId")
        cache.remove("s:$artId")
    }

    private fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    private data class CompactArtwork(val extension: String, val bytes: ByteArray)

    private fun compactArtwork(bytes: ByteArray): CompactArtwork {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return CompactArtwork("jpg", bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return CompactArtwork("jpg", bytes)
        return try {
            val out = ByteArrayOutputStream()
            val success = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, out)
            val encoded = out.toByteArray()
            if (success && encoded.isNotEmpty() && encoded.size + 32 < bytes.size) CompactArtwork("webp", encoded)
            else CompactArtwork("jpg", bytes)
        } finally {
            bitmap.recycle()
        }
    }

    private fun fileForLarge(artId: String): File = File(dir, safeId(artId) + "_lg.jpg")
    private fun fileForSmallWebp(artId: String): File = File(dir, safeId(artId) + "_sm.webp")
    private fun fileForSmallJpeg(artId: String): File = File(dir, safeId(artId) + "_sm.jpg")

    private fun cacheCapacityBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8L).coerceIn(8L * MB, 64L * MB).toInt()

    private companion object {
        const val MB = 1024L * 1024L
        const val MAX_DISK_CACHE_BYTES = 128L * MB
    }
}
