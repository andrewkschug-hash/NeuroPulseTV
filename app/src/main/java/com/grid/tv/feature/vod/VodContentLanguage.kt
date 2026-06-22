package com.grid.tv.feature.vod

import java.util.Locale

private val VOD_TITLE_PREFIX_PATTERN = Regex("""^([A-Z0-9]{2,3})\s*[-–—]\s*""", RegexOption.IGNORE_CASE)
private val VOD_SUFFIX_LANGUAGE_PATTERN = Regex("""\s*[-–—]\s*([A-Z]{2,3})\s*$""", RegexOption.IGNORE_CASE)
private val VOD_BRACKET_PREFIX_PATTERN = Regex("""^\[([A-Z]{2,3})\]\s*[-–—]?\s*""", RegexOption.IGNORE_CASE)
private val VOD_PAREN_PREFIX_PATTERN = Regex("""^\(([A-Z]{2,3})\)\s*[-–—]?\s*""", RegexOption.IGNORE_CASE)
private val VOD_PIPE_WRAP_PREFIX_PATTERN = Regex("""^\|([A-Z]{2,3})\|\s*""", RegexOption.IGNORE_CASE)
private val VOD_LANG_PIPE_PREFIX_PATTERN = Regex("""^([A-Z]{2,3})\s*[|｜]\s*""", RegexOption.IGNORE_CASE)
private val VOD_TRAILING_LANGUAGE_PATTERN = Regex("""\s*\(([A-Z]{2,3})\)\s*$""", RegexOption.IGNORE_CASE)
private val VOD_TRAILING_TAG_PATTERN = Regex("""\s*\(([^)]+)\)\s*$""")
private val VOD_YEAR_VALUE = Regex("""19\d{2}|20\d{2}""")
private val VOD_LANGUAGE_CODE = Regex("""^[A-Z]{2,3}$""")

private val NON_LANGUAGE_CODES = setOf("4K", "HD", "UHD", "NF")

/**
 * Parses a language/region code from IPTV stream titles and category names.
 * Matches the badge shown on VOD poster cards (prefix, suffix, bracket, pipe, trailing tags).
 */
fun parseVodContentLanguageCode(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    extractPrefixLanguage(trimmed)?.let { return it }
    extractSuffixLanguage(trimmed)?.let { return it }
    extractLastParentheticalLanguage(trimmed)?.let { return it }
    return null
}

private fun extractPrefixLanguage(trimmed: String): String? {
    VOD_TITLE_PREFIX_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    VOD_BRACKET_PREFIX_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    VOD_PAREN_PREFIX_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    VOD_PIPE_WRAP_PREFIX_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    VOD_LANG_PIPE_PREFIX_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    return null
}

private fun extractSuffixLanguage(trimmed: String): String? {
    VOD_TRAILING_LANGUAGE_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    VOD_SUFFIX_LANGUAGE_PATTERN.find(trimmed)?.let { match ->
        normalizeLanguageCode(match.groupValues[1])?.let { return it }
    }
    return null
}

private fun extractLastParentheticalLanguage(trimmed: String): String? {
    var text = trimmed
    var candidate: String? = null
    while (true) {
        val match = VOD_TRAILING_TAG_PATTERN.find(text) ?: break
        val inner = match.groupValues[1].trim().uppercase()
        text = text.removeRange(match.range).trim()
        if (inner.matches(VOD_YEAR_VALUE)) continue
        normalizeLanguageCode(inner)?.let { candidate = it }
    }
    return candidate
}

private fun normalizeLanguageCode(raw: String): String? {
    val code = raw.trim().uppercase()
    if (!code.matches(VOD_LANGUAGE_CODE)) return null
    if (code in NON_LANGUAGE_CODES) return null
    return code.take(2)
}

fun displayLanguageName(code: String): String {
    val locale = Locale.forLanguageTag(code.lowercase())
    val name = locale.getDisplayLanguage(Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && !name.equals(code, ignoreCase = true) } ?: code.uppercase()
}

fun discoverLanguageCodesFromLabels(labels: Sequence<String>): List<String> =
    labels.mapNotNull { parseVodContentLanguageCode(it) }
        .distinctBy { it.uppercase() }
        .sortedBy { displayLanguageName(it).lowercase() }
        .toList()

/** Strips IPTV language/resolution markers from stream titles (mapping layer — not UI). */
fun stripVodLanguageMarkers(raw: String): String =
    raw.trim()
        .replace(VOD_TITLE_PREFIX_PATTERN, "")
        .replace(VOD_BRACKET_PREFIX_PATTERN, "")
        .replace(VOD_PAREN_PREFIX_PATTERN, "")
        .replace(VOD_PIPE_WRAP_PREFIX_PATTERN, "")
        .replace(VOD_LANG_PIPE_PREFIX_PATTERN, "")
        .replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
        .replace(VOD_TRAILING_LANGUAGE_PATTERN, " ")
        .replace(VOD_SUFFIX_LANGUAGE_PATTERN, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
