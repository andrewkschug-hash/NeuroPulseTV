package com.grid.tv.ui.component

import com.grid.tv.domain.model.genreIntegratedRowTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodTitleFormatterTest {

    @Test
    fun cleanVodDisplayTitle_stripsLanguagePrefix() {
        assertEquals("Inception", cleanVodDisplayTitle("EN - Inception"))
        assertEquals("Amélie", cleanVodDisplayTitle("FR - Amélie"))
    }

    @Test
    fun cleanVodDisplayTitle_stripsResolutionPrefix() {
        assertEquals("Dune", cleanVodDisplayTitle("4K - Dune"))
    }

    @Test
    fun parseVodLanguageBadge_readsPrefixCode() {
        assertEquals("EN", parseVodLanguageBadge("EN - Movie"))
        assertEquals("FR", parseVodLanguageBadge("FR - Movie"))
        assertNull(parseVodLanguageBadge("4K - Movie"))
    }

    @Test
    fun parseVodResolutionBadge_detects4kAndHd() {
        assertEquals("4K", parseVodResolutionBadge("4K - Movie"))
        assertEquals("HD", parseVodResolutionBadge("HD - Movie"))
    }

    @Test
    fun genreIntegratedRowTitle_usesCategoryNameWithoutTopPrefix() {
        assertEquals("NETFLIX ASIA", genreIntegratedRowTitle("1006", "NETFLIX ASIA"))
        assertEquals("Apple+ Kids", genreIntegratedRowTitle("kids", "Apple+ Kids"))
        assertEquals("Continue Watching", genreIntegratedRowTitle("continue_watching", "Continue Watching"))
    }
}
