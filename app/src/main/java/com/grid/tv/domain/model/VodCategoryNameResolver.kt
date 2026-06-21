package com.grid.tv.domain.model

/**
 * Maps Xtream category IDs to human-readable labels for sidebar chips and browse rows.
 *
 * Providers sometimes store only numeric [VodCategory.id] values in the name column (backfill
 * paths, missing `category_name`, or `category_name` mirroring the id). This resolver keeps
 * filtering keyed by id while displaying a readable label.
 */
object VodCategoryNameResolver {

    fun isUnresolvedName(categoryId: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.equals(categoryId.trim(), ignoreCase = true)) return true
        return trimmed.all { it.isDigit() }
    }

    fun compositeKey(playlistId: Long, categoryId: String): String = "${playlistId}_$categoryId"

    /**
     * Builds an id → label lookup from categories whose names are already human-readable.
     */
    fun buildLookupTable(categories: Iterable<VodCategory>): Map<String, String> {
        val lookup = linkedMapOf<String, String>()
        categories.forEach { category ->
            if (!isUnresolvedName(category.id, category.name)) {
                lookup[compositeKey(category.playlistId, category.id)] = category.name.trim()
                lookup.putIfAbsent(category.id, category.name.trim())
            }
        }
        return lookup
    }

    fun mergeLookupTables(vararg tables: Map<String, String>): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        tables.forEach { table ->
            table.forEach { (key, value) ->
                if (value.isNotBlank() && !isUnresolvedName(key.substringAfterLast('_'), value)) {
                    merged[key] = value.trim()
                }
            }
        }
        return merged
    }

    fun resolveDisplayName(
        categoryId: String,
        storedName: String,
        playlistId: Long = 0L,
        lookupById: Map<String, String> = emptyMap()
    ): String {
        if (!isUnresolvedName(categoryId, storedName)) return storedName.trim()

        val composite = compositeKey(playlistId, categoryId)
        lookupById[composite]?.takeIf { !isUnresolvedName(categoryId, it) }?.let { return it.trim() }
        lookupById[categoryId]?.takeIf { !isUnresolvedName(categoryId, it) }?.let { return it.trim() }

        return storedName.trim().ifBlank { categoryId }
    }

    fun normalizeList(categories: List<VodCategory>): List<VodCategory> {
        if (categories.isEmpty()) return categories
        val lookup = buildLookupTable(categories)
        return categories.map { category ->
            category.copy(
                name = resolveDisplayName(
                    categoryId = category.id,
                    storedName = category.name,
                    playlistId = category.playlistId,
                    lookupById = lookup
                )
            )
        }
    }

    fun withResolvedNames(
        category: VodCategory,
        lookupById: Map<String, String>
    ): VodCategory = category.copy(
        name = resolveDisplayName(
            categoryId = category.id,
            storedName = category.name,
            playlistId = category.playlistId,
            lookupById = lookupById
        )
    )
}
