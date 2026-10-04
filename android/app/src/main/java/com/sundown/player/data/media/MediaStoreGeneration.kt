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

/**
 * MediaStore IDs without a volume component belong to the legacy/external-primary
 * collection. Keep IDs for absent removable volumes until those volumes are scanned again.
 */
internal fun mediaStoreIdWasInScannedVolumes(id: String, scannedVolumes: Set<String>): Boolean {
    if (!id.startsWith("ms:")) return false
    val key = id.removePrefix("ms:")
    if (key.toLongOrNull() != null) {
        return "external" in scannedVolumes || "external_primary" in scannedVolumes
    }
    val separator = key.lastIndexOf(':')
    if (separator <= 0 || key.substring(separator + 1).toLongOrNull() == null) return false
    return key.substring(0, separator) in scannedVolumes
}

internal fun removedMediaStoreIds(
    previousIds: Set<String>,
    currentIds: Set<String>,
    scannedVolumes: Set<String>,
): List<String> = previousIds.filter { id ->
    id !in currentIds && mediaStoreIdWasInScannedVolumes(id, scannedVolumes)
}

/** Keep generation watermarks for detached volumes, but discard stale checkpoints for volumes we did scan. */
internal fun mergeMediaStoreCheckpoints(
    previous: Map<String, MediaStoreCheckpoint>,
    scannedVolumes: Set<String>,
    current: Map<String, MediaStoreCheckpoint>,
): Map<String, MediaStoreCheckpoint> = buildMap {
    previous.forEach { (volume, checkpoint) ->
        if (volume !in scannedVolumes && volume !in current) put(volume, checkpoint)
    }
    putAll(current)
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
