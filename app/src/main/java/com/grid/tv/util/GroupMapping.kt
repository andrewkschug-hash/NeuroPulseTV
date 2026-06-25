package com.grid.tv.util

/**
 * Maps raw M3U / Xtream group-tag strings to broader parent categories for the
 * channel-group sidebar. Does not affect stored group names or channel filtering.
 */
const val PARENT_GROUP_ADULT = "Adult (18+)"

private val ADULT_GROUP_KEYWORDS = listOf(
    "xxx",
    "adult",
    "porn",
    "for adult",
    "18+",
    "pink",
    "mature",
    "erotic",
    "sex",
    "redlight",
    "red light",
)

fun isAdultChannelGroup(rawGroup: String): Boolean {
    val lower = rawGroup.trim().lowercase()
    if (lower.isEmpty()) return false
    return ADULT_GROUP_KEYWORDS.any { keyword -> lower.contains(keyword) }
}

fun resolveParentGroup(rawGroup: String): String {
    val normalized = rawGroup.trim().replace("|", "").trim()
    if (normalized.isEmpty()) return PARENT_GROUP_OTHER
    if (isAdultChannelGroup(normalized)) return PARENT_GROUP_ADULT

    val upper = normalized.uppercase()

    regionParentFor(upper)?.let { return it }

    if (containsKeyword(upper, "SPORT", "FOOT", "NFL", "NBA", "MLB")) return "Sports"
    if (containsKeyword(upper, "NEWS", "CNN", "BBC")) return "News"
    if (containsKeyword(upper, "MOVIE", "FILM", "CINEMA")) return "Movies"
    if (upper == "24/7" || upper.contains("24/7")) return "24/7 Channels"
    if (upper == "4K" || upper.contains("4K")) return "4K Channels"
    if (containsKeyword(upper, "KIDS", "CHILD", "CARTOON")) return "Kids"

    return PARENT_GROUP_OTHER
}

/** Preferred display order for parent categories in the sidebar. */
val PARENT_GROUP_DISPLAY_ORDER: List<String> = listOf(
    "Africa",
    "Americas",
    "Europe",
    "UK",
    "USA",
    "Canada",
    "Australia",
    "New Zealand",
    "Middle East",
    "Sports",
    "News",
    "Movies",
    "24/7 Channels",
    "4K Channels",
    "Kids",
    PARENT_GROUP_OTHER,
    PARENT_GROUP_ADULT
)

private const val PARENT_GROUP_OTHER = "Other"

private fun containsKeyword(upper: String, vararg keywords: String): Boolean =
    keywords.any { keyword -> upper.contains(keyword) }

private fun regionParentFor(upper: String): String? {
    tokenRegionParent(firstGroupToken(upper))?.let { return it }
    if (upper.contains("AFR")) return "Africa"
    return null
}

private fun firstGroupToken(upper: String): String =
    upper.split(Regex("""[\s❖\-–]+"""))
        .firstOrNull()
        ?.trim()
        .orEmpty()

private fun tokenRegionParent(token: String): String? = when (token) {
    "AFR" -> "Africa"
    "AL" -> "Europe"
    "AM" -> "Americas"
    "EU" -> "Europe"
    "UK", "GB" -> "UK"
    "US", "USA" -> "USA"
    "CA" -> "Canada"
    "AU" -> "Australia"
    "NZ" -> "New Zealand"
    "ME" -> "Middle East"
    else -> null
}

fun parentGroupSortIndex(parent: String): Int {
    val index = PARENT_GROUP_DISPLAY_ORDER.indexOf(parent)
    return if (index >= 0) index else PARENT_GROUP_DISPLAY_ORDER.size
}
