package com.sundown.player.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreGenerationTest {
    @Test
    fun matchingVersionProducesExclusiveInclusiveDeltaWindow() {
        assertEquals(
            MediaStoreGenerationRange(after = 42L, through = 57L),
            mediaStoreGenerationRange(MediaStoreCheckpoint("v1", 42L), 57L, "v1"),
        )
    }

    @Test
    fun equalWatermarkProducesAnEmptyButValidDeltaWindow() {
        assertEquals(
            MediaStoreGenerationRange(after = 57L, through = 57L),
            mediaStoreGenerationRange(MediaStoreCheckpoint("v1", 57L), 57L, "v1"),
        )
    }

    @Test
    fun missingChangedOrRolledBackCheckpointRequiresFullSnapshot() {
        assertNull(mediaStoreGenerationRange(null, 57L, "v1"))
        assertNull(mediaStoreGenerationRange(MediaStoreCheckpoint("v1", 42L), null, "v1"))
        assertNull(mediaStoreGenerationRange(MediaStoreCheckpoint("v1", -1L), 57L, "v1"))
        assertNull(mediaStoreGenerationRange(MediaStoreCheckpoint("v1", 58L), 57L, "v1"))
        assertNull(mediaStoreGenerationRange(MediaStoreCheckpoint("old", 42L), 57L, "new"))
        assertNull(mediaStoreGenerationRange(MediaStoreCheckpoint("v1", 42L), 57L, null))
    }

    @Test
    fun absentRemovableVolumesDoNotDeleteCachedTracks() {
        val previous = setOf("ms:7", "ms:ABCD-1234:9", "ms:WXYZ-5678:11")
        val current = setOf("ms:8", "ms:ABCD-1234:9")

        assertEquals(
            listOf("ms:7"),
            removedMediaStoreIds(previous, current, setOf("external_primary", "ABCD-1234")),
        )
        assertEquals(
            listOf("ms:7"),
            removedMediaStoreIds(previous, current, setOf("external_primary")),
        )
    }

    @Test
    fun detachedVolumeCheckpointIsRetainedWithoutKeepingStaleMountedCheckpoint() {
        val primary = MediaStoreCheckpoint("primary-v2", 57L)
        val mountedButUncheckpointed = MediaStoreCheckpoint("old-card-v1", 12L)
        val detached = MediaStoreCheckpoint("detached-v3", 81L)
        val currentPrimary = MediaStoreCheckpoint("primary-v2", 63L)

        assertEquals(
            mapOf("external_primary" to currentPrimary, "WXYZ-5678" to detached),
            mergeMediaStoreCheckpoints(
                previous = mapOf(
                    "external_primary" to primary,
                    "ABCD-1234" to mountedButUncheckpointed,
                    "WXYZ-5678" to detached,
                ),
                scannedVolumes = setOf("external_primary", "ABCD-1234"),
                current = mapOf("external_primary" to currentPrimary),
            ),
        )
    }

    @Test
    fun volumeCheckpointsRoundTripInStableOrder() {
        val checkpoints = linkedMapOf(
            "ABCD-1234" to MediaStoreCheckpoint("opaque|version=一", 81L),
            "external_primary" to MediaStoreCheckpoint("v2", 57L),
        )
        val encoded = encodeMediaStoreCheckpoints(checkpoints)

        val reversed = checkpoints.entries.reversed().associate { it.key to it.value }
        assertEquals(encoded, encodeMediaStoreCheckpoints(reversed))
        assertEquals(checkpoints.toSortedMap(), decodeMediaStoreCheckpoints(encoded))
        assertEquals(
            mapOf("external_primary" to MediaStoreCheckpoint("v2", 57L)),
            decodeMediaStoreCheckpoints("bad|81|@@@\n${encodeMediaStoreCheckpoints(mapOf("external_primary" to checkpoints.getValue("external_primary")))}"),
        )
    }
}
