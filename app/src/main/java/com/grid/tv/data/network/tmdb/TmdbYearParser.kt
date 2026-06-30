package com.grid.tv.data.network.tmdb

/**
 * Extracts release year from IPTV titles.
 * Only accepts years in parentheses or near the end — ignores stray leading digits (e.g. "007").
 */
object TmdbYearParser {

    private val PAREN_YEAR_AT_END = Regex("""\(\s*(19\d{2}|20\d{2})\s*\)\s*$""")
    private val TRAILING_YEAR = Regex("""(?:\s|[-–—|])\s*((19\d{2}|20\d{2}))\s*$""")
    private val END_YEAR = Regex("""\b(19\d{2}|20\d{2})\s*$""")

    fun parse(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        PAREN_YEAR_AT_END.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        TRAILING_YEAR.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        END_YEAR.find(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        // Parenthetical year anywhere (e.g. "Inception (2010) 4K")
        Regex("""\(\s*(19\d{2}|20\d{2})\s*\)""").find(trimmed)
            ?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

        return null
    }

    fun yearFromTmdbDate(date: String?): Int? {
        val value = date?.trim().orEmpty()
        if (value.length < 4) return null
        return value.take(4).toIntOrNull()?.takeIf { it in 1900..2099 }
    }
}
