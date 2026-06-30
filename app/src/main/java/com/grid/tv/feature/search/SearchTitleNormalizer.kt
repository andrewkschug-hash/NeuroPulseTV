package com.grid.tv.feature.search

import com.grid.tv.feature.vod.stripVodLanguageMarkers
import java.util.Locale

/**
 * Builds [searchTitle] values stored at import time and SQL prefix patterns for indexed lookup.
 */
object SearchTitleNormalizer {

    private val QUALITY_TAG_PATTERN = Regex(
        """\b(4k|uhd|fhd|hd|sd|hevc|x265|x264|h\.?265|h\.?264|hdr|dv|atmos|multi\s*sub|multi)\b""",
        RegexOption.IGNORE_CASE
    )
    private val PAREN_TAG_PATTERN = Regex("""\s*\([^)]*\)\s*""")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9\\s]")
    private val WHITESPACE = Regex("\\s+")

    /** Strip IPTV noise, lowercase, collapse whitespace — stored in DB. */
    fun normalize(raw: String): String {
        var text = stripVodLanguageMarkers(raw)
        text = QUALITY_TAG_PATTERN.replace(text, " ")
        while (PAREN_TAG_PATTERN.containsMatchIn(text)) {
            text = PAREN_TAG_PATTERN.replace(text, " ")
        }
        return text
            .lowercase(Locale.US)
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }

    /** Prefix pattern for `LIKE :pattern ESCAPE '\\'` — empty when query is blank. */
    fun toSqlPrefixPattern(rawQuery: String): String {
        val normalized = normalize(rawQuery)
        if (normalized.isEmpty()) return ""
        return "$normalized%"
    }
}
