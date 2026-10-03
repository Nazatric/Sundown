package com.sundown.player.ui.components

/**
 * Formats non-negative milliseconds as M:SS or H:MM:SS without locale-sensitive
 * formatting. The same helper is used by library rows, sheets and both player
 * timelines so hour-long tracks never change shape between screens.
 */
fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return buildString(8) {
        if (hours > 0L) {
            append(hours)
            append(':')
            appendTwoDigits(minutes)
        } else {
            append(minutes)
        }
        append(':')
        appendTwoDigits(seconds)
    }
}

private fun StringBuilder.appendTwoDigits(value: Long) {
    if (value < 10L) append('0')
    append(value)
}
