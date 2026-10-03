package com.sundown.player.ui

/** Only the fields library rows need; high-frequency progress stays in the player UI. */
data class LibraryPlaybackState(
    val trackId: String? = null,
    val playing: Boolean = false,
)
