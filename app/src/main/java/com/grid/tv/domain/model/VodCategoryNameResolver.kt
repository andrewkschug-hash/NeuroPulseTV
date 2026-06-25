package com.grid.tv.domain.model

/**
 * Maps Xtream category IDs to human-readable labels for sidebar chips and browse rows.
 *
 * Providers sometimes store only numeric [VodCategory.id] values in the name column (backfill
 * paths, missing `category_name`, or `category_name` mirroring the id). This resolver keeps
 * filtering keyed by id while displaying a readable label.
 *
 * UI grouping and lookup use [categoryKey] (playlist-scoped). Bare [VodCategory.id] remains
 * the DAO filter parameter.
 */
object VodCategoryNameResolver {

    /** @see VodSidebarGenreNormalizer.formatDisplayName */
    fun formatSidebarGenreName(name: String): String =
        VodSidebarGenreNormalizer.formatDisplayName(name)

    /** @see VodSidebarGenreNormalizer.comparisonKey */
    fun sidebarGenreComparisonKey(name: String): String =
        VodSidebarGenreNormalizer.comparisonKey(name)

    fun categoryKey(playlistId: Long, categoryId: String): String =
        com.grid.tv.domain.model.categoryKey(playlistId, categoryId)

    /** @see categoryKey */
    fun compositeKey(playlistId: Long, categoryId: String): String = categoryKey(playlistId, categoryId)

    fun categoryBrowseRowId(playlistId: Long, categoryId: String): String =
        com.grid.tv.domain.model.categoryBrowseRowId(playlistId, categoryId)

    fun isUnresolvedName(categoryId: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.equals(categoryId.trim(), ignoreCase = true)) return true
        return trimmed.all { it.isDigit() }
    }

    /**
     * Builds a playlist-scoped lookup from categories whose names are already human-readable.
     * Bare [VodCategory.id] is stored only as a last-resort compatibility fallback.
     */
    fun buildLookupTable(categories: Iterable<VodCategory>): Map<String, String> {
        val lookup = linkedMapOf<String, String>()
        categories.forEach { category ->
            if (!isUnresolvedName(category.id, category.name)) {
                val name = category.name.trim()
                lookup[categoryKey(category.playlistId, category.id)] = name
                lookup.putIfAbsent(category.id, name)
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

        val scoped = categoryKey(playlistId, categoryId)
        lookupById[scoped]?.takeIf { !isUnresolvedName(categoryId, it) }?.let { return it.trim() }
        lookupById[categoryId]?.takeIf { !isUnresolvedName(categoryId, it) }?.let { return it.trim() }

        return storedName.trim().ifBlank { categoryId }
    }

    fun normalizeList(categories: List<VodCategory>): List<VodCategory> {
        if (categories.isEmpty()) return categories
        val streamBacked = VodCategoryGuards.filterStreamBacked(categories, source = "normalizeList")
        val lookup = buildLookupTable(streamBacked)
        return streamBacked.map { category ->
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

    data class SeriesSidebarCategories(
        val displayCategories: List<VodCategory>,
        /** Keys are [categoryKey] values; values are bare category ids for DAO filtering. */
        val filterIdsByRepresentativeId: Map<String, Set<String>>
    )

    /**
     * Collapses raw series categories from DB, stream fallback, and genre hints to one row per
     * [categoryKey] before any display-name resolution.
     */
    fun mergeSeriesCategorySources(vararg sources: List<VodCategory>): List<VodCategory> {
        val merged = linkedMapOf<String, VodCategory>()
        sources.forEach { source ->
            VodCategoryGuards.sanitizeStreamBacked(source).forEach { category ->
                if (category.id.isBlank()) return@forEach
                val key = categoryKey(category.playlistId, category.id)
                val existing = merged[key]
                merged[key] = preferCategoryForMerge(existing, category)
            }
        }
        return merged.values.toList()
    }

    private fun preferCategoryForMerge(existing: VodCategory?, incoming: VodCategory): VodCategory {
        if (existing == null) return incoming
        val existingReadable = !isUnresolvedName(existing.id, existing.name)
        val incomingReadable = !isUnresolvedName(incoming.id, incoming.name)
        return when {
            incomingReadable && !existingReadable -> incoming
            existingReadable && !incomingReadable -> existing
            incomingReadable && incoming.name.length > existing.name.length -> incoming
            else -> existing
        }
    }

    fun prepareSeriesCategoriesForSidebar(categories: List<VodCategory>): SeriesSidebarCategories {
        if (categories.isEmpty()) {
            return SeriesSidebarCategories(emptyList(), emptyMap())
        }
        val merged = mergeSeriesCategorySources(categories)
            .distinctBy { categoryKey(it.playlistId, it.id) }
        return prepareCategoriesForSidebar(merged, source = "seriesSidebar")
    }

    /** Movies sidebar: same display-name collapse and multi-id filter mapping as series. */
    fun prepareMovieCategoriesForSidebar(categories: List<VodCategory>): SeriesSidebarCategories {
        if (categories.isEmpty()) {
            return SeriesSidebarCategories(emptyList(), emptyMap())
        }
        val streamBacked = VodCategoryGuards.sanitizeStreamBacked(categories)
            .distinctBy { categoryKey(it.playlistId, it.id) }
        return prepareCategoriesForSidebar(streamBacked, source = "moviesSidebar")
    }

    /**
     * Resolves labels, then collapses to one sidebar row per playlist-scoped display name.
     * Multiple stream-backed category ids sharing a label map to the same filter id set.
     */
    fun prepareCategoriesForSidebar(
        categories: List<VodCategory>,
        source: String = "sidebar"
    ): SeriesSidebarCategories {
        if (categories.isEmpty()) {
            return SeriesSidebarCategories(emptyList(), emptyMap())
        }
        val lookup = buildLookupTable(categories)
        val resolved = categories.map { category ->
            category.copy(
                name = formatSidebarGenreName(
                    resolveDisplayName(
                        categoryId = category.id,
                        storedName = category.name,
                        playlistId = category.playlistId,
                        lookupById = lookup
                    )
                )
            )
        }

        val grouped = resolved.groupBy { VodSidebarGenreNormalizer.scopedComparisonKey(it.playlistId, it.name) }
        VodSidebarGenreNormalizer.logMergedGenreGroups(source, grouped)

        val filterIdsByRepresentativeId = linkedMapOf<String, Set<String>>()
        val displayCategories = grouped.values
            .map { group -> pickSidebarRepresentative(group) }
            .map { representative ->
                val canonicalName = VodSidebarGenreNormalizer.pickCanonicalDisplayName(
                    resolved.filter {
                        it.playlistId == representative.playlistId &&
                            VodSidebarGenreNormalizer.comparisonKey(it.name) ==
                            VodSidebarGenreNormalizer.comparisonKey(representative.name)
                    }.map { it.name }
                )
                val displayCategory = representative.copy(name = canonicalName)
                val repKey = categoryKey(displayCategory.playlistId, displayCategory.id)
                val comparisonKey = VodSidebarGenreNormalizer.comparisonKey(canonicalName)
                val siblingIds = resolved
                    .filter {
                        it.playlistId == displayCategory.playlistId &&
                            VodSidebarGenreNormalizer.comparisonKey(it.name) == comparisonKey
                    }
                    .map { it.id }
                    .toSet()
                filterIdsByRepresentativeId[repKey] = siblingIds
                displayCategory
            }
            .sortedBy { it.name.lowercase() }

        return SeriesSidebarCategories(displayCategories, filterIdsByRepresentativeId)
    }

    private fun pickSidebarRepresentative(group: List<VodCategory>): VodCategory {
        return group.minWithOrNull(
            compareBy<VodCategory> { isUnresolvedName(it.id, it.name) }
                .thenBy { it.id.length }
                .thenBy { it.id }
        ) ?: group.first()
    }
}
