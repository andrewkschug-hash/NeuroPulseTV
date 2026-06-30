package com.grid.tv.feature.guide

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartGroupClassifierTest {

    @Test
    fun classify_detectsUsSports() {
        val result = SmartGroupClassifier.classify("US | Sports HD")
        assertEquals("United States", result.country)
        assertEquals("Sports", result.category)
    }

    @Test
    fun classify_detectsUkNews() {
        val result = SmartGroupClassifier.classify("UK - News")
        assertEquals("United Kingdom", result.country)
        assertEquals("News", result.category)
    }

    @Test
    fun classify_moviesWithoutCountry_isInternational() {
        val result = SmartGroupClassifier.classify("Movies 4K")
        assertEquals(SmartGroupClassifier.COUNTRY_INTERNATIONAL, result.country)
        assertEquals("Movies", result.category)
    }
}
