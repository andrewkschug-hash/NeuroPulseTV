package com.grid.tv.domain.model

/**
 * Xtream `category_id` / `category_ids` normalization.
 *
 * Panels inconsistently return ids as JSON numbers (`45`) or strings (`"45"`), sometimes
 * mixed across `get_*_categories` vs `get_vod_streams` / `get_series` on the same server.
 * Always canonicalize to a trimmed decimal string before map keys or equality checks so
 * type mismatch cannot silently bucket every title under "Uncategorized".
 */
object VodCategoryId {
    /** Sentinel / missing category from many panels. */
    private val MISSING = setOf("0", "null", "nil", "none")

    /**
     * Normalize one id token to a stable string key, or null when genuinely absent.
     * Strips trailing `.0` from numeric stringifications.
     */
    fun canonicalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val normalized = if (trimmed.endsWith(".0") && trimmed.dropLast(2).all { it.isDigit() }) {
            trimmed.dropLast(2)
        } else {
            trimmed
        }
        if (normalized.isBlank()) return null
        if (normalized.lowercase() in MISSING) return null
        return normalized
    }

    fun canonicalizeNumber(value: Number): String? =
        canonicalize(value.toLong().toString())

    /** Encode membership list for SQL / storage (`12,45,67`). */
    fun toCsv(ids: Collection<String>): String? {
        val normalized = ids.mapNotNull { canonicalize(it) }.distinct()
        if (normalized.isEmpty()) return null
        return normalized.joinToString(",")
    }

    fun fromCsv(csv: String?): List<String> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(',')
            .mapNotNull { canonicalize(it) }
            .distinct()
    }

    /** Primary id is the first membership entry (Xtream singular `category_id` preference). */
    fun primaryOf(ids: Collection<String>): String? =
        ids.mapNotNull { canonicalize(it) }.firstOrNull()
}
