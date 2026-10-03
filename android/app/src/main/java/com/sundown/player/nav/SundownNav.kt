package com.sundown.player.nav

import android.net.Uri

/**
 * Native destinations.
 *
 * Tabs are intentionally **not** destinations. They are top-level views, so
 * pressing back on a tab leaves the app instead of walking tab history — the
 * behavior explicitly retained to avoid tab-history back loops through
 * Songs -> Albums -> Playlists. This matches Android's guidance for top-level
 * destinations.
 *
 * Everything the user drills *into* is a real destination and pops in order:
 *   Library -> Playlist -> NowPlaying -> back -> Playlist -> back -> Library
 */
sealed class Route(val path: String) {
    data object Library : Route("library")
    data object Sources : Route("sources")
    data object NowPlaying : Route("now-playing")
    data object Queue : Route("queue")
    data object NewPlaylist : Route("new-playlist?seed={seed}")

    data object Album : Route("album/{key}") {
        fun of(key: String) = "album/" + Uri.encode(key)
    }

    data object Playlist : Route("playlist/{id}") {
        fun of(id: String) = "playlist/" + Uri.encode(id)
    }

    data object Chooser : Route("chooser/{trackId}") {
        fun of(trackId: String) = "chooser/" + Uri.encode(trackId)
    }

    /** Artist / genre drill-in. Back restores the unfiltered library. */
    data object Filtered : Route("filtered?artist={artist}&genre={genre}") {
        fun artist(key: String) = "filtered?artist=" + Uri.encode(key) + "&genre="
        fun genre(name: String) = "filtered?artist=&genre=" + Uri.encode(name)
    }

    companion object {
        fun newPlaylist(seedTrackId: String? = null) =
            "new-playlist?seed=" + Uri.encode(seedTrackId.orEmpty())
    }
}
