package com.grid.tv.domain.model

import android.util.Log

/**
 * Defensive checks for VOD category identity. Filtering always uses stream-backed
 * [VodCategory.id] values (Xtream `category_id`); display names and genre hints are labels only.
 */
object VodCategoryGuards {

    private const val LOG_TAG = "VodCategoryGuard"

    /** Log tag message for duplicate labels mapping to different ids within a playlist. */
    const val COLLISION_LOG_MESSAGE = "Category name collision: same label, different IDs"

    /**
     * True when [categoryId] looks like an Xtream `category_id` token, not a genre/display label.
     * Genre hints must never synthesize new ids — they only label existing stream-backed pairs.
     */
    fun isStreamBackedCategoryId(categoryId: String?): Boolean {
        if (categoryId.isNullOrBlank()) return false
        val id = categoryId.trim()
        if (id.contains(' ') || id.contains('/') || id.contains('&')) return false
        if (id.length > 64) return false
        return true
    }

    fun partitionStreamBacked(categories: List<VodCategory>): Pair<List<VodCategory>, List<VodCategory>> =
        categories.partition { isStreamBackedCategoryId(it.id) }

    fun findDisplayNameCollisions(categories: List<VodCategory>): List<DisplayNameCollision> {
        val idsByScopedLabel = linkedMapOf<String, MutableSet<String>>()
        categories.forEach { category ->
            if (category.name.isBlank() || !isStreamBackedCategoryId(category.id)) return@forEach
            val labelKey = VodSidebarGenreNormalizer.scopedPrimaryComparisonKey(
                category.playlistId,
                category.name,
            )
            idsByScopedLabel.getOrPut(labelKey) { linkedSetOf() }.add(category.id)
        }
        return idsByScopedLabel.mapNotNull { (labelKey, ids) ->
            if (ids.size <= 1) return@mapNotNull null
            val playlistId = labelKey.substringBefore(':').toLongOrNull() ?: 0L
            val label = labelKey.substringAfter(':')
            DisplayNameCollision(playlistId = playlistId, label = label, categoryIds = ids.toList())
        }
    }

    fun sanitizeStreamBacked(categories: List<VodCategory>): List<VodCategory> =
        partitionStreamBacked(categories).first

    fun filterStreamBacked(
        categories: List<VodCategory>,
        source: String,
        logCollisions: Boolean = false,
    ): List<VodCategory> {
        if (categories.isEmpty()) return categories
        val (valid, dropped) = partitionStreamBacked(categories)
        dropped.forEach { category ->
            Log.w(
                LOG_TAG,
                "dropped non-stream-backed categoryId source=$source " +
                    "playlist=${category.playlistId} id='${category.id}' name='${category.name}'"
            )
        }
        if (logCollisions) {
            logDisplayNameCollisions(valid, source)
        }
        return valid
    }

    /** Non-crashing debug assertion: same display label + playlist with multiple distinct ids. */
    fun logDisplayNameCollisions(categories: List<VodCategory>, source: String = "unknown") {
        findDisplayNameCollisions(categories).forEach { collision ->
            Log.w(
                LOG_TAG,
                "$COLLISION_LOG_MESSAGE source=$source playlist=${collision.playlistId} " +
                    "label='${collision.label}' ids=${collision.categoryIds}"
            )
        }
    }

    data class DisplayNameCollision(
        val playlistId: Long,
        val label: String,
        val categoryIds: List<String>
    )
}
