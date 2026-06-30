package com.grid.tv.feature.guide

import java.util.Locale

/**
 * Maps raw IPTV provider group tags to canonical display names used for smart navigation.
 * Applied when building the per-playlist smart-group cache at import time.
 */
object GroupNameNormalizer {

    private val COUNTRY_ALIASES = mapOf(
        "usa" to "United States",
        "us" to "United States",
        "u s" to "United States",
        "u s a" to "United States",
        "united states" to "United States",
        "america" to "United States",
        "uk" to "United Kingdom",
        "gb" to "United Kingdom",
        "gbr" to "United Kingdom",
        "england" to "United Kingdom",
        "united kingdom" to "United Kingdom",
        "great britain" to "United Kingdom",
        "ca" to "Canada",
        "can" to "Canada",
        "canada" to "Canada",
        "fr" to "France",
        "fra" to "France",
        "france" to "France",
        "de" to "Germany",
        "deu" to "Germany",
        "ger" to "Germany",
        "germany" to "Germany",
        "es" to "Spain",
        "esp" to "Spain",
        "spain" to "Spain",
        "it" to "Italy",
        "ita" to "Italy",
        "italy" to "Italy",
        "au" to "Australia",
        "aus" to "Australia",
        "australia" to "Australia",
        "nz" to "New Zealand",
        "new zealand" to "New Zealand",
        "mx" to "Mexico",
        "mexico" to "Mexico",
        "br" to "Brazil",
        "brazil" to "Brazil",
        "in" to "India",
        "india" to "India",
        "ae" to "United Arab Emirates",
        "uae" to "United Arab Emirates",
        "ar" to "Argentina",
        "argentina" to "Argentina",
        "nl" to "Netherlands",
        "netherlands" to "Netherlands",
        "be" to "Belgium",
        "belgium" to "Belgium",
        "pt" to "Portugal",
        "portugal" to "Portugal",
        "pl" to "Poland",
        "poland" to "Poland",
        "tr" to "Turkey",
        "turkey" to "Turkey",
        "gr" to "Greece",
        "greece" to "Greece",
        "ie" to "Ireland",
        "ireland" to "Ireland",
        "za" to "South Africa",
        "south africa" to "South Africa",
        "africa" to "Africa",
        "europe" to "Europe",
        "asia" to "Asia",
        "middle east" to "Middle East",
        "latam" to "Latin America",
        "latin america" to "Latin America",
    )

    private val CATEGORY_ALIASES = mapOf(
        "movie" to "Movies",
        "movies" to "Movies",
        "film" to "Movies",
        "films" to "Movies",
        "cinema" to "Movies",
        "vod" to "Movies",
        "sport" to "Sports",
        "sports" to "Sports",
        "football" to "Sports",
        "soccer" to "Sports",
        "news" to "News",
        "kid" to "Kids",
        "kids" to "Kids",
        "children" to "Kids",
        "cartoon" to "Kids",
        "entertainment" to "Entertainment",
        "ent" to "Entertainment",
        "documentary" to "Documentary",
        "docs" to "Documentary",
        "music" to "Music",
        "ppv" to "PPV",
        "pay per view" to "PPV",
        "adult" to "Adult",
        "xxx" to "Adult",
        "18" to "Adult",
        "general" to "General",
        "misc" to "General",
        "other" to "General",
    )

    fun normalize(raw: String): String {
        val cleaned = raw.trim()
            .replace("|", " ")
            .replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return cleaned
        val lower = cleaned.lowercase(Locale.US)
        COUNTRY_ALIASES[lower]?.let { return it }
        CATEGORY_ALIASES[lower]?.let { return it }
        return cleaned.split(" ", "❖", "-", "–", "|", ":")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                val tokenLower = token.lowercase(Locale.US)
                COUNTRY_ALIASES[tokenLower]
                    ?: CATEGORY_ALIASES[tokenLower]
                    ?: token.lowercase(Locale.US).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                    }
            }
    }

    fun normalizeToken(token: String): String? {
        val lower = token.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return null
        return COUNTRY_ALIASES[lower] ?: CATEGORY_ALIASES[lower]
    }
}
