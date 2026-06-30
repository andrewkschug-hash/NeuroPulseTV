package com.grid.tv.data.network.tmdb

import com.grid.tv.feature.vod.stripVodLanguageMarkers
import com.grid.tv.ui.component.cleanVodDisplayTitle

/** Prepares IPTV stream titles for TMDB search — never send raw provider strings. */
object TmdbTitleNormalizer {

    private val QUALITY_TAG_PATTERN = Regex(
        """\b(4k|uhd|fhd|hd|sd|hevc|x265|x264|h\.?265|h\.?264|hdr|dv|atmos|multi\s*sub|multi)\b""",
        RegexOption.IGNORE_CASE
    )

    fun normalizeForSearch(raw: String): String =
        stripVodLanguageMarkers(cleanVodDisplayTitle(raw))
            .replace(QUALITY_TAG_PATTERN, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Extra strip for retry attempts — removes parenthetical tags and quality noise. */
    fun stripForRetry(title: String): String {
        var text = title
        while (Regex("""\s*\([^)]*\)""").containsMatchIn(text)) {
            text = Regex("""\s*\([^)]*\)""").replace(text, " ")
        }
        return text
            .replace(Regex("""\b(4k|uhd|fhd|hd|hevc|x265|x264|multi\s*sub)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun normalizeForComparison(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
