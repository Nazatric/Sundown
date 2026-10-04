package com.sundown.player.ui

import androidx.compose.runtime.Immutable

/** Only the fields library rows need; high-frequency progress stays in the player UI. */
@Immutable
data class LibraryPlaybackState(
    val trackId: String? = null,
    val playing: Boolean = false,
)
