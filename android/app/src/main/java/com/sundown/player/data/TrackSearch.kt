package com.sundown.player.data

import com.sundown.player.data.db.TrackEntity

/** The one authoritative field set for exact search and the optional FTS index. */
internal fun searchDocumentText(
    title: String,
    artist: String,
    album: String,
    albumArtist: String,
    genre: String,
): String = "$title $artist $album $albumArtist $genre"

internal fun TrackEntity.searchDocumentText(): String =
    searchDocumentText(title, artist, album, albumArtist, genre)

/** Preserve Sundown's case-insensitive substring behavior, including short queries. */
internal fun TrackEntity.matchesSearch(query: String): Boolean =
    query.isBlank() || searchDocumentText().contains(query, ignoreCase = true)

/** Keep the unsynchronized-index fallback exact rather than returning stale positives. */
internal fun TrackEntity.matchesPendingSearch(indexedText: String?, query: String): Boolean =
    indexedText != searchDocumentText() && matchesSearch(query)
