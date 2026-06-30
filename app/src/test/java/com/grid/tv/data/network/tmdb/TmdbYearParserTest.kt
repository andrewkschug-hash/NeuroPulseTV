package com.grid.tv.data.network.tmdb

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbYearParserTest {

    @Test
    fun parse_parentheticalYear() {
        assertEquals(2010, TmdbYearParser.parse("Inception (2010)"))
        assertEquals(2010, TmdbYearParser.parse("EN - Inception (2010) 4K"))
    }

    @Test
    fun parse_trailingYear() {
        assertEquals(1995, TmdbYearParser.parse("12 Monkeys 1995"))
        assertEquals(2007, TmdbYearParser.parse("300 - 2007"))
    }

    @Test
    fun parse_ignoresLeadingDigits() {
        assertNull(TmdbYearParser.parse("007 Spectre"))
        assertNull(TmdbYearParser.parse("24 Live Another Day"))
    }

    @Test
    fun parse_ignoresYearEmbeddedInTitle() {
        assertNull(TmdbYearParser.parse("2012 Disaster Movie"))
    }
}
