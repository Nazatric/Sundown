package com.sundown.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetIndexKeyTest {
    @Test
    fun stripsTheBeforeChoosingLetter() {
        assertEquals("K", alphabetIndexKey("The Killers"))
    }

    @Test
    fun mapsAsciiLettersCaseInsensitively() {
        assertEquals("A", alphabetIndexKey("  alice"))
        assertEquals("Z", alphabetIndexKey("Zappa"))
    }

    @Test
    fun mapsNonAsciiAndNonLettersToHashBucket() {
        assertEquals("#", alphabetIndexKey("Édith Piaf"))
        assertEquals("#", alphabetIndexKey("3 Doors Down"))
        assertEquals("#", alphabetIndexKey(""))
    }
}
