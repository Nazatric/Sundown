package com.sundown.player.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {
    @Test
    fun formatsZeroAndSubMinuteDurations() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(999L))
        assertEquals("0:01", formatDuration(1_000L))
        assertEquals("0:59", formatDuration(59_999L))
    }

    @Test
    fun formatsMinutesAndHoursWithStablePadding() {
        assertEquals("1:00", formatDuration(60_000L))
        assertEquals("59:59", formatDuration(3_599_999L))
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("12:03:04", formatDuration(43_384_000L))
    }

    @Test
    fun clampsNegativeAndVeryLongDurationsWithoutOverflow() {
        assertEquals("0:00", formatDuration(-1L))
        assertEquals("2562047788015:12:55", formatDuration(Long.MAX_VALUE))
    }
}
