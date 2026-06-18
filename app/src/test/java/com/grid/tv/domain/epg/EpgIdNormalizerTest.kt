package com.grid.tv.domain.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgIdNormalizerTest {

    @Test
    fun normalize_stripsCaseSpacesAndPunctuation() {
        assertEquals("bbconeuk", EpgIdNormalizer.normalize("BBC.One.UK"))
        assertEquals("espn", EpgIdNormalizer.normalize("ESPN"))
        assertEquals("espn", EpgIdNormalizer.normalize("  ESPN  "))
        assertEquals("", EpgIdNormalizer.normalize(null))
        assertEquals("", EpgIdNormalizer.normalize("   "))
    }
}
