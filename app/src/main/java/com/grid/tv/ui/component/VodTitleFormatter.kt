package com.grid.tv.ui.component

private val VOD_TITLE_PREFIX_PATTERN = Regex("""^([A-Z0-9]{2,3})\s*-\s*""", RegexOption.IGNORE_CASE)
private val VOD_YEAR_PATTERN = Regex("""\b(19\d{2}|20\d{2})\b""")

fun cleanVodDisplayTitle(raw: String): String =
    raw.trim()
        .replace(VOD_TITLE_PREFIX_PATTERN, "")
        .replace(Regex("""\s*\(\d{4}\)\s*"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

fun parseVodLanguageBadge(raw: String): String? {
    val match = VOD_TITLE_PREFIX_PATTERN.find(raw.trim()) ?: return null
    val code = match.groupValues[1].uppercase()
    return when (code) {
        "4K", "HD", "UHD", "NF" -> null
        else -> code.take(2)
    }
}

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
