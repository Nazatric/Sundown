package com.sundown.player.data

import com.sundown.player.data.db.TrackEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSearchTest {
    private val track = TrackEntity(
        id = "test-track",
        docUri = "content://local/test-track",
        path = "Music/Evening.mp3",
        name = "Evening.mp3",
        size = 1L,
        mtime = 1L,
        sourceVersion = null,
        title = "Evening Light",
        artist = "The Northern Lights",
        album = "First Horizon",
        albumArtist = "Aurora Collective",
        genre = "Ambient",
        trackNo = 1,
        discNo = 1,
        year = 2026,
        duration = 180,
        artId = null,
        albumKey = "aurora collective :: first horizon",
        artistKey = "aurora collective",
        source = "file",
        addedAt = 1L,
    )

    @Test
    fun albumArtistIsIncludedInIndexedSearchDocument() {
        assertTrue(track.searchDocumentText().contains("Aurora Collective"))
    }

    @Test
    fun exactSearchPreservesCaseInsensitiveSubstringSemanticsAcrossFields() {
        assertTrue(track.matchesSearch("RORA COL"))
        assertTrue(track.matchesSearch("evening li"))
        assertTrue(track.matchesSearch("ambient"))
        assertFalse(track.matchesSearch("aurora collectives"))
    }

    @Test
    fun pendingIndexFallbackReturnsOnlyExactSubstringMatches() {
        val updated = track.copy(title = "Morning Light")
        val staleIndexedText = track.searchDocumentText()

        assertTrue(updated.matchesPendingSearch(staleIndexedText, "morning li"))
        assertFalse(updated.matchesPendingSearch(staleIndexedText, "missing phrase"))
        assertFalse(track.matchesPendingSearch(track.searchDocumentText(), "evening"))
    }

    @Test
    fun blankSearchMatchesEveryTrack() {
        assertTrue(track.matchesSearch(""))
        assertTrue(track.matchesSearch("   "))
    }
}
