package com.grid.tv.feature.vod

import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.domain.model.buildVodWallRows
import com.grid.tv.domain.model.vodHomeLeadWallRowIds

/**
 * Pre-split VOD catalogs built off the UI thread after sync / language filtering.
 * Tab switches swap references into these arrays instead of re-filtering at render time.
 */
data class VodCatalogPartitions(
    val movieBrowseRows: List<VodBrowseRow> = emptyList(),
    val seriesBrowseRows: List<VodBrowseRow> = emptyList(),
    val allPersonalizedWallRows: List<VodWallRow> = emptyList(),
    val allStaticWallRows: List<VodWallRow> = emptyList(),
    val movieWallRows: List<VodWallRow> = emptyList(),
    val seriesWallRows: List<VodWallRow> = emptyList(),
    val movieSidebarBundle: VodCategoryNameResolver.SeriesSidebarCategories =
        VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap()),
    val seriesSidebarBundle: VodCategoryNameResolver.SeriesSidebarCategories =
        VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap()),
    val revision: String = "",
) {
    fun wallRowsFor(filter: VodContentFilter): List<VodWallRow> = when (filter) {
        VodContentFilter.ALL -> orderedAllWallRows()
        VodContentFilter.MOVIES -> movieWallRows
        VodContentFilter.SERIES -> seriesWallRows
        VodContentFilter.SEARCH -> emptyList()
    }

    /** Builds tab-specific wall rows on demand when startup prefetch was skipped. */
    fun ensureWallRowsFor(filter: VodContentFilter): VodCatalogPartitions = when (filter) {
        VodContentFilter.MOVIES -> {
            if (movieWallRows.isNotEmpty() || movieBrowseRows.isEmpty()) {
                this
            } else {
                copy(
                    movieWallRows = buildVodWallRows(
                        filter = VodContentFilter.MOVIES,
                        continueWatching = emptyList(),
                        trendingMovies = emptyList(),
                        recommendedMovies = emptyList(),
                        movieBrowseRows = movieBrowseRows,
                        seriesBrowseRows = emptyList(),
                    )
                )
            }
        }
        VodContentFilter.SERIES -> {
            if (seriesWallRows.isNotEmpty() || seriesBrowseRows.isEmpty()) {
                this
            } else {
                copy(
                    seriesWallRows = buildVodWallRows(
                        filter = VodContentFilter.SERIES,
                        continueWatching = emptyList(),
                        trendingMovies = emptyList(),
                        recommendedMovies = emptyList(),
                        movieBrowseRows = emptyList(),
                        seriesBrowseRows = seriesBrowseRows,
                    )
                )
            }
        }
        else -> this
    }

    private fun orderedAllWallRows(): List<VodWallRow> {
        val personalizedById = allPersonalizedWallRows.associateBy { it.id }
        return buildList {
            personalizedById["continue_watching"]?.let { add(it) }
            allStaticWallRows.firstOrNull { it.id == "movie_recent" }?.let { add(it) }
            allStaticWallRows
                .filter { it.id !in vodHomeLeadWallRowIds }
                .forEach { add(it) }
            personalizedById["trending"]?.let { add(it) }
            personalizedById["recommended"]?.let { add(it) }
        }
    }

    fun wallRowsRevisionFor(filter: VodContentFilter): String = when (filter) {
        VodContentFilter.ALL -> "${revision}_all"
        VodContentFilter.MOVIES -> "${revision}_movies"
        VodContentFilter.SERIES -> "${revision}_series"
        VodContentFilter.SEARCH -> "${revision}_search"
    }

    /** Approximate retained wall-item count — used for trim logging. */
    fun estimatedWallItemCount(): Int =
        allPersonalizedWallRows.sumOf { it.items.size } +
            allStaticWallRows.sumOf { it.items.size } +
            movieWallRows.sumOf { it.items.size } +
            seriesWallRows.sumOf { it.items.size }

    /**
     * Drops pre-built wall rows and browse slices that are not needed for [activeFilter].
     * Browse rows needed for the active tab/sidebar are retained.
     */
    fun trimForMemoryPressure(activeFilter: VodContentFilter): VodCatalogPartitions = when (activeFilter) {
        VodContentFilter.ALL -> copy(
            movieWallRows = emptyList(),
            seriesWallRows = emptyList(),
        )
        VodContentFilter.MOVIES -> copy(
            allPersonalizedWallRows = emptyList(),
            allStaticWallRows = emptyList(),
            seriesWallRows = emptyList(),
            seriesBrowseRows = emptyList(),
            seriesSidebarBundle = VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap()),
        )
        VodContentFilter.SERIES -> copy(
            allPersonalizedWallRows = emptyList(),
            allStaticWallRows = emptyList(),
            movieWallRows = emptyList(),
            movieBrowseRows = emptyList(),
            movieSidebarBundle = VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap()),
        )
        VodContentFilter.SEARCH -> copy(
            allPersonalizedWallRows = emptyList(),
            allStaticWallRows = emptyList(),
            movieWallRows = emptyList(),
            seriesWallRows = emptyList(),
            movieBrowseRows = emptyList(),
            seriesBrowseRows = emptyList(),
            movieSidebarBundle = VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap()),
            seriesSidebarBundle = VodCategoryNameResolver.SeriesSidebarCategories(emptyList(), emptyMap()),
        )
    }

    companion object {
        val EMPTY = VodCatalogPartitions()
    }
}

data class VodCatalogPartitionInputs(
    val movieBrowseRows: List<VodBrowseRow>,
    val seriesBrowseRows: List<VodBrowseRow>,
    val movieCategories: List<VodCategory>,
    val seriesCategories: List<VodCategory>,
    val continueWatching: List<ContinueWatchingItem>,
    val trendingMovies: List<VodItem>,
    val recommendedMovies: List<VodItem>,
    val movieBrowseIndex: VodBrowseRowCategoryIndex = VodBrowseRowCategoryIndex.EMPTY,
    val seriesBrowseIndex: VodBrowseRowCategoryIndex = VodBrowseRowCategoryIndex.EMPTY,
)

fun buildVodCatalogPartitions(
    inputs: VodCatalogPartitionInputs,
    prefetchTabWallRows: Boolean = true,
): VodCatalogPartitions {
    val movieBrowseRows = inputs.movieBrowseRows
    val seriesBrowseRows = inputs.seriesBrowseRows

    val movieBrowseIndex = inputs.movieBrowseIndex.takeUnless { it == VodBrowseRowCategoryIndex.EMPTY }
        ?: VodBrowseRowCategoryIndex.fromBrowseRows(movieBrowseRows)
    val seriesBrowseIndex = inputs.seriesBrowseIndex.takeUnless { it == VodBrowseRowCategoryIndex.EMPTY }
        ?: VodBrowseRowCategoryIndex.fromBrowseRows(seriesBrowseRows)

    val allPersonalizedWallRows = buildVodWallRows(
        filter = VodContentFilter.ALL,
        continueWatching = inputs.continueWatching,
        trendingMovies = inputs.trendingMovies,
        recommendedMovies = inputs.recommendedMovies,
        movieBrowseRows = emptyList(),
        seriesBrowseRows = emptyList()
    )
    val allStaticWallRows = buildVodWallRows(
        filter = VodContentFilter.ALL,
        continueWatching = emptyList(),
        trendingMovies = emptyList(),
        recommendedMovies = emptyList(),
        movieBrowseRows = movieBrowseRows,
        seriesBrowseRows = seriesBrowseRows
    )
    val movieWallRows = if (prefetchTabWallRows && movieBrowseRows.isNotEmpty()) {
        buildVodWallRows(
            filter = VodContentFilter.MOVIES,
            continueWatching = emptyList(),
            trendingMovies = emptyList(),
            recommendedMovies = emptyList(),
            movieBrowseRows = movieBrowseRows,
            seriesBrowseRows = emptyList()
        )
    } else {
        emptyList()
    }
    val seriesWallRows = if (prefetchTabWallRows && seriesBrowseRows.isNotEmpty()) {
        buildVodWallRows(
            filter = VodContentFilter.SERIES,
            continueWatching = emptyList(),
            trendingMovies = emptyList(),
            recommendedMovies = emptyList(),
            movieBrowseRows = emptyList(),
            seriesBrowseRows = seriesBrowseRows
        )
    } else {
        emptyList()
    }

    val movieSidebarBundle = prepareMovieSidebarCategories(
        primary = inputs.movieCategories,
        browseIndex = movieBrowseIndex,
    )
    val seriesSidebarBundle = prepareSeriesSidebarCategories(
        primary = inputs.seriesCategories,
        browseIndex = seriesBrowseIndex,
    )

    val revision = buildString {
        append(movieBrowseRows.joinToString(",") { "${it.id}:${it.movies.size}" })
        append('|')
        append(seriesBrowseRows.joinToString(",") { "${it.id}:${it.series.size}" })
        append('|')
        append(allPersonalizedWallRows.joinToString(",") { "${it.id}:${it.items.size}" })
    }

    return VodCatalogPartitions(
        movieBrowseRows = movieBrowseRows,
        seriesBrowseRows = seriesBrowseRows,
        allPersonalizedWallRows = allPersonalizedWallRows,
        allStaticWallRows = allStaticWallRows,
        movieWallRows = movieWallRows,
        seriesWallRows = seriesWallRows,
        movieSidebarBundle = movieSidebarBundle,
        seriesSidebarBundle = seriesSidebarBundle,
        revision = revision
    )
}
