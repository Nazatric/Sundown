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
