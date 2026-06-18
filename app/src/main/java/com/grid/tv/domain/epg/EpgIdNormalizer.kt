package com.grid.tv.domain.epg

/**
 * Normalizes EPG / TVG identifiers for fuzzy comparison.
 * Strips case, whitespace, and non-alphanumeric characters so values like
 * `BBC.One.HD`, `bbc one hd`, and `BBCOneHD` compare equal.
 */
object EpgIdNormalizer {
    fun normalize(id: String?): String {
        if (id.isNullOrBlank()) return ""
        return id.lowercase()
            .trim()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }
}
