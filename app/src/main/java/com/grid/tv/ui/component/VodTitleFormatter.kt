package com.grid.tv.ui.component

import com.grid.tv.feature.vod.parseVodContentLanguageCode

private val VOD_TITLE_PREFIX_PATTERN = Regex("""^([A-Z0-9]{2,3})\s*-\s*""", RegexOption.IGNORE_CASE)
private val VOD_YEAR_PATTERN = Regex("""\b(19\d{2}|20\d{2})\b""")
private val VOD_TRAILING_LANGUAGE_PATTERN = Regex("""\s*\(([A-Z]{2,3})\)\s*$""")
private val VOD_TRAILING_TAG_PATTERN = Regex("""\s*\(([^)]+)\)\s*$""")
private val VOD_YEAR_VALUE = Regex("""19\d{2}|20\d{2}""")

fun formatVodPlayerOverlayTitle(raw: String): String {
    var text = raw.trim().replace(VOD_TITLE_PREFIX_PATTERN, "").trim()
    while (true) {
        val match = VOD_TRAILING_TAG_PATTERN.find(text) ?: break
        val inner = match.groupValues[1].trim()
        if (inner.matches(VOD_YEAR_VALUE)) break
        text = text.removeRange(match.range).trim()
    }
    return text.replace(Regex("""\s+"""), " ").trim()
}

fun parseVodStreamTagBadge(raw: String): String? {
    parseVodLanguageBadge(raw)?.let { return it }
    parseVodResolutionBadge(raw)?.let { return it }
    val trimmed = raw.trim()
    VOD_TRAILING_TAG_PATTERN.find(trimmed)?.let { match ->
        val inner = match.groupValues[1].trim()
        if (!inner.matches(VOD_YEAR_VALUE)) {
            return inner.take(16)
        }
    }
    return null
}

fun cleanVodDisplayTitle(raw: String): String =
    raw.trim()
        .replace(VOD_TITLE_PREFIX_PATTERN, "")
        .replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
        .replace(VOD_TRAILING_LANGUAGE_PATTERN, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

fun parseVodLanguageBadge(raw: String): String? = parseVodContentLanguageCode(raw)

fun parseVodResolutionBadge(raw: String): String? {
    val trimmed = raw.trim()
    val upper = trimmed.uppercase()
    return when {
        VOD_TITLE_PREFIX_PATTERN.find(trimmed)?.groupValues?.get(1)?.equals("4K", true) == true -> "4K"
        upper.startsWith("4K -") || upper.contains(" 4K ") || upper.contains("(4K)") -> "4K"
        VOD_TITLE_PREFIX_PATTERN.find(trimmed)?.groupValues?.get(1)?.equals("HD", true) == true -> "HD"
        upper.startsWith("HD -") -> "HD"
        upper.contains("2160P") -> "4K"
        upper.contains("1080P") -> "HD"
        else -> null
    }
}

fun parseVodReleaseYear(raw: String): String? =
    VOD_YEAR_PATTERN.find(raw)?.value

fun formatVodGenreTags(raw: String?): String? =
    raw?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?.take(4)
        ?.joinToString(" · ")
        ?.takeIf { it.isNotBlank() }

fun formatSearchRatingLabel(rating: String?): String? {
    val trimmed = rating?.trim()?.takeIf { it.isNotBlank() } ?: return null
    trimmed.toDoubleOrNull()?.let { value ->
        if (value <= 0.0) return null
        return if (value % 1.0 == 0.0) "★ ${value.toInt()}" else "★ ${String.format("%.1f", value)}"
    }
    if (trimmed == "0" || trimmed.equals("0.0", ignoreCase = true)) return null
    return "★ $trimmed"
}

fun buildMovieSearchSecondaryLine(genre: String?, rating: String?): String =
    listOfNotNull("Movie", genre?.takeIf { it.isNotBlank() }, formatSearchRatingLabel(rating))
        .joinToString(" · ")
