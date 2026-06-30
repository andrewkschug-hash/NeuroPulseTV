package com.grid.tv.feature.guide

import com.grid.tv.util.isAdultChannelGroup

/** Classifies a provider group name into country + content category for smart navigation. */
object SmartGroupClassifier {

    const val COUNTRY_INTERNATIONAL = "International"
    const val CATEGORY_GENERAL = "General"

    private val COUNTRY_ORDER = listOf(
        "United States",
        "United Kingdom",
        "Canada",
        "France",
        "Germany",
        "Spain",
        "Italy",
        "Australia",
        "New Zealand",
        "Mexico",
        "Brazil",
        "India",
        "Netherlands",
        "Belgium",
        "Portugal",
        "Poland",
        "Turkey",
        "Greece",
        "Ireland",
        "Argentina",
        "South Africa",
        "United Arab Emirates",
        "Africa",
        "Europe",
        "Asia",
        "Middle East",
        "Latin America",
        COUNTRY_INTERNATIONAL,
    )

    private val CATEGORY_ORDER = listOf(
        "Sports",
        "News",
        "Movies",
        "Kids",
        "Entertainment",
        "Music",
        "Documentary",
        "PPV",
        "Adult",
        CATEGORY_GENERAL,
    )

    data class Classification(
        val country: String,
        val category: String,
        val normalizedName: String,
    )

    fun classify(rawGroupName: String): Classification {
        val normalized = GroupNameNormalizer.normalize(rawGroupName)
        if (normalized.isBlank()) {
            return Classification(COUNTRY_INTERNATIONAL, CATEGORY_GENERAL, normalized)
        }
        if (isAdultChannelGroup(rawGroupName) || isAdultChannelGroup(normalized)) {
            return Classification(COUNTRY_INTERNATIONAL, "Adult", normalized)
        }

        val tokens = tokenize(rawGroupName)
        val country = detectCountry(rawGroupName, normalized, tokens)
        val category = detectCategory(rawGroupName, normalized, tokens)
        return Classification(country = country, category = category, normalizedName = normalized)
    }

    fun countrySortIndex(country: String): Int {
        val index = COUNTRY_ORDER.indexOf(country)
        return if (index >= 0) index else COUNTRY_ORDER.size
    }

    fun categorySortIndex(category: String): Int {
        val index = CATEGORY_ORDER.indexOf(category)
        return if (index >= 0) index else CATEGORY_ORDER.size
    }

    private fun tokenize(raw: String): List<String> =
        raw.uppercase()
            .split(Regex("""[\s❖\-–|:]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun detectCountry(raw: String, normalized: String, tokens: List<String>): String {
        GroupNameNormalizer.normalizeToken(normalized)?.let { canonical ->
            if (canonical in COUNTRY_ORDER) return canonical
        }
        tokens.forEach { token ->
            GroupNameNormalizer.normalizeToken(token)?.let { canonical ->
                if (canonical in COUNTRY_ORDER) return canonical
            }
        }
        val upper = raw.uppercase()
        val normalizedUpper = normalized.uppercase()
        when {
            containsAny(upper, "USA", "U.S", "UNITED STATES") -> return "United States"
            containsAny(upper, "UK", "UNITED KINGDOM", "GREAT BRITAIN", "ENGLAND") -> return "United Kingdom"
            containsAny(upper, "CANADA", " CAN ") -> return "Canada"
            containsAny(upper, "FRANCE", " FR ") -> return "France"
            containsAny(upper, "GERMANY", " DE ") -> return "Germany"
            containsAny(upper, "SPAIN", " ES ") -> return "Spain"
            containsAny(upper, "ITALY", " IT ") -> return "Italy"
            containsAny(upper, "AUSTRALIA", " AU ") -> return "Australia"
            containsAny(upper, "NEW ZEALAND", " NZ ") -> return "New Zealand"
            containsAny(upper, "MEXICO", " MX ") -> return "Mexico"
            containsAny(upper, "BRAZIL", " BR ") -> return "Brazil"
            containsAny(upper, "INDIA", " IN ") -> return "India"
            containsAny(upper, "AFRICA", "AFR") -> return "Africa"
            containsAny(upper, "EUROPE", " EU ") -> return "Europe"
            containsAny(upper, "MIDDLE EAST", " ME ") -> return "Middle East"
            containsAny(upper, "LATIN", "LATAM") -> return "Latin America"
        }
        if (normalizedUpper in COUNTRY_ORDER) return normalized
        return COUNTRY_INTERNATIONAL
    }

    private fun detectCategory(raw: String, normalized: String, tokens: List<String>): String {
        GroupNameNormalizer.normalizeToken(normalized)?.let { canonical ->
            if (canonical in CATEGORY_ORDER) return canonical
        }
        tokens.forEach { token ->
            GroupNameNormalizer.normalizeToken(token)?.let { canonical ->
                if (canonical in CATEGORY_ORDER) return canonical
            }
        }
        val upper = raw.uppercase()
        return when {
            containsAny(upper, "PPV", "PAY PER VIEW", "PAY-PER-VIEW") -> "PPV"
            containsAny(upper, "SPORT", "NFL", "NBA", "MLB", "FOOTBALL", "SOCCER") -> "Sports"
            containsAny(upper, "NEWS", "CNN", "BBC", "HEADLINE") -> "News"
            containsAny(upper, "MOVIE", "FILM", "CINEMA", "VOD") -> "Movies"
            containsAny(upper, "KID", "CHILD", "CARTOON", "FAMILY") -> "Kids"
            containsAny(upper, "ENTERTAIN") -> "Entertainment"
            containsAny(upper, "MUSIC", "MTV") -> "Music"
            containsAny(upper, "DOC") -> "Documentary"
            else -> CATEGORY_GENERAL
        }
    }

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { needle -> haystack.contains(needle) }
}
