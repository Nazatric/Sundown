package com.sundown.player.data.media

import java.nio.charset.StandardCharsets
import java.util.Base64

/** An opaque MediaStore version paired with the latest successful volume generation. */
data class MediaStoreCheckpoint(val version: String, val generation: Long)

/** Exclusive lower / inclusive upper MediaStore generation window. */
internal data class MediaStoreGenerationRange(val after: Long, val through: Long)

/** A missing/changed version or missing/negative/rolled-back watermark requires a full snapshot. */
internal fun mediaStoreGenerationRange(
    checkpoint: MediaStoreCheckpoint?,
    through: Long?,
    currentVersion: String?,
): MediaStoreGenerationRange? {
    if (checkpoint == null || currentVersion.isNullOrBlank() || checkpoint.version != currentVersion) return null
    val after = checkpoint.generation
    if (through == null || after < 0L || through < after) return null
    return MediaStoreGenerationRange(after, through)
}

/** Stable, dependency-free encoding for per-volume DataStore checkpoints. */
internal fun encodeMediaStoreCheckpoints(checkpoints: Map<String, MediaStoreCheckpoint>): String =
    checkpoints.toSortedMap().entries.joinToString("\n") { (volume, checkpoint) ->
        "${encodeToken(volume)}|${checkpoint.generation}|${encodeToken(checkpoint.version)}"
    }

internal fun decodeMediaStoreCheckpoints(encoded: String): Map<String, MediaStoreCheckpoint> =
    encoded.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 3) return@mapNotNull null
        val volume = decodeToken(parts[0]) ?: return@mapNotNull null
        val generation = parts[1].toLongOrNull() ?: return@mapNotNull null
        val version = decodeToken(parts[2]) ?: return@mapNotNull null
        if (volume.isBlank() || version.isBlank() || generation < 0L) null
        else volume to MediaStoreCheckpoint(version, generation)
    }.toMap()

private fun encodeToken(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeToken(value: String): String? = runCatching {
    String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}.getOrNull()
