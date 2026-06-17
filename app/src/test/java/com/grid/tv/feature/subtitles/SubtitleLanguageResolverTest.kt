package com.grid.tv.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SubtitleLanguageResolverTest {

    @Test
    fun priorityLanguages_ordersUserDeviceThenEnglish() {
        val priorities = SubtitleLanguageResolver.priorityLanguages("es", Locale("de", "DE"))
        assertEquals(listOf("es", "de", "en"), priorities)
    }

    @Test
    fun pickBestLanguage_prefersExactMatch() {
        val chosen = SubtitleLanguageResolver.pickBestLanguage(
            available = listOf("fre", "eng", "spa"),
            priorities = listOf("en", "es")
        )
        assertEquals("eng", chosen)
    }

    @Test
    fun pickBestLanguage_returnsNullWhenEmpty() {
        assertNull(SubtitleLanguageResolver.pickBestLanguage(emptyList(), listOf("en")))
    }
}
