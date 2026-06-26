package com.grid.tv.domain.model

import android.util.Log
import java.util.Locale

/**
 * Normalizes VOD sidebar genre labels for grouping without over-merging distinct categories.
 *
 * Only normalizes case, whitespace, and light punctuation variants (e.g. "and" vs "&").
 * Does not strip quality suffixes such as HD, FHD, or 4K.
 */
object VodSidebarGenreNormalizer {

    private const val LOG_TAG = "VodSidebarGenre"

    private val WHITESPACE = Regex("\\s+")
    private val AND_SEPARATOR = Regex("\\s+and\\s+", RegexOption.IGNORE_CASE)
    private val AMPERSAND = Regex("\\s*&\\s*")
    private val SLASH = Regex("\\s*/\\s*")

    /** Trim, collapse whitespace, and normalize common separator punctuation for display. */
    fun formatDisplayName(name: String): String {
        if (name.isBlank()) return name.trim()
        return name.trim()
            .replace(WHITESPACE, " ")
            .replace(AND_SEPARATOR, " & ")
            .replace(AMPERSAND, " & ")
            .replace(SLASH, " / ")
            .trim()
    }

    /** Case-insensitive comparison key used before collapsing sidebar rows. */
    fun comparisonKey(name: String): String =
        formatDisplayName(name).lowercase(Locale.ROOT)

    /** Case-insensitive key for the primary genre token (before comma/slash chains). */
    fun primaryComparisonKey(name: String): String {
        val formatted = formatDisplayName(name)
        val primary = formatted
            .substringBefore(',')
            .substringBefore('/')
            .trim()
            .ifBlank { formatted }
        return comparisonKey(primary)
    }

    fun scopedPrimaryComparisonKey(playlistId: Long, name: String): String =
        "$playlistId:${primaryComparisonKey(name)}"

    fun scopedComparisonKey(playlistId: Long, name: String): String =
        "$playlistId:${comparisonKey(name)}"

    /** Lower ranks sort earlier; subtitle/language buckets sink to the bottom. */
    fun sidebarSortRank(name: String): Int {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("hindi") && lower.contains("sub") -> 3
            lower.contains("subs") || lower.contains("subtitle") -> 2
            lower.contains(" dubbed") || lower.endsWith(" dubbed") -> 2
            else -> 0
        }
    }

    /**
     * Picks a readable canonical label from variants that share the same [comparisonKey].
     * Prefers mixed/title case over ALL CAPS and " & " over " and " via [formatDisplayName].
     */
    fun pickCanonicalDisplayName(variants: Collection<String>): String {
        if (variants.isEmpty()) return ""
        val formatted = variants.map { formatDisplayName(it) }
        return formatted.minWithOrNull(
            compareBy<String> { titleCaseScore(it) }
                .thenBy { it.length }
                .thenBy { it.lowercase(Locale.ROOT) }
        ) ?: formatted.first()
    }

    /** Lower is better; ALL CAPS scores highest (worst). */
    private fun titleCaseScore(name: String): Int {
        val letters = name.filter { it.isLetter() }
        if (letters.isEmpty()) return 0
        val lowercaseCount = letters.count { it.isLowerCase() }
        return when {
            lowercaseCount == 0 -> 2
            lowercaseCount == letters.length -> 1
            else -> 0
        }
    }

    fun logMergedGenreGroups(
        source: String,
        groups: Map<String, List<VodCategory>>
    ) {
        groups.values
            .filter { it.size > 1 }
            .forEach { group ->
                val playlistId = group.first().playlistId
                val variants = group.map { it.name }.distinct()
                val ids = group.map { it.id }.distinct().sorted()
                val canonical = pickCanonicalDisplayName(variants)
                val message =
                    "merged sidebar genre source=$source playlist=$playlistId " +
                        "canonical='$canonical' variants=${variants.sorted()} ids=$ids"
                logMerge(message)
            }
    }

    /** Override in tests; on device uses [Log.i]. */
    internal var mergeLogHandler: ((String) -> Unit)? = null

    private fun logMerge(message: String) {
        mergeLogHandler?.invoke(message) ?: runCatching { Log.i(LOG_TAG, message) }
    }
}
