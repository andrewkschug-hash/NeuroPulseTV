package com.grid.tv.feature.vod

import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodCategoryNameResolver
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.VodWallRow
import com.grid.tv.domain.model.buildVodWallRows

/**
 * Pre-split VOD catalogs built off the UI thread after sync / language filtering.
 * Tab switches swap references into these arrays instead of re-filtering at render time.
 */
data class VodCatalogPartitions(
    val movieBrowseRows: List<VodBrowseRow> = emptyList(),
    val seriesBrowseRows: List<VodBrowseRow> = emptyList(),
    /** Flat movie list derived from [movieBrowseRows]. */
    val movieCatalog: List<VodItem> = emptyList(),
    /** Flat series list derived from [seriesBrowseRows]. */
    val seriesCatalog: List<SeriesShow> = emptyList(),
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
        VodContentFilter.ALL -> allPersonalizedWallRows + allStaticWallRows
        VodContentFilter.MOVIES -> movieWallRows
        VodContentFilter.SERIES -> seriesWallRows
        VodContentFilter.SEARCH -> emptyList()
    }

    fun wallRowsRevisionFor(filter: VodContentFilter): String = when (filter) {
        VodContentFilter.ALL -> "${revision}_all"
        VodContentFilter.MOVIES -> "${revision}_movies"
        VodContentFilter.SERIES -> "${revision}_series"
        VodContentFilter.SEARCH -> "${revision}_search"
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
)

fun buildVodCatalogPartitions(inputs: VodCatalogPartitionInputs): VodCatalogPartitions {
    val movieBrowseRows = inputs.movieBrowseRows
    val seriesBrowseRows = inputs.seriesBrowseRows
    val movieCatalog = movieBrowseRows.flatMap { row -> row.movies }
    val seriesCatalog = seriesBrowseRows.flatMap { row -> row.series }

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
    val movieWallRows = buildVodWallRows(
        filter = VodContentFilter.MOVIES,
        continueWatching = emptyList(),
        trendingMovies = emptyList(),
        recommendedMovies = emptyList(),
        movieBrowseRows = movieBrowseRows,
        seriesBrowseRows = emptyList()
    )
    val seriesWallRows = buildVodWallRows(
        filter = VodContentFilter.SERIES,
        continueWatching = emptyList(),
        trendingMovies = emptyList(),
        recommendedMovies = emptyList(),
        movieBrowseRows = emptyList(),
        seriesBrowseRows = seriesBrowseRows
    )

    val movieSidebarBundle = prepareMovieSidebarCategories(inputs.movieCategories, movieBrowseRows)
    val seriesSidebarBundle = prepareSeriesSidebarCategories(inputs.seriesCategories, seriesBrowseRows)

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
        movieCatalog = movieCatalog,
        seriesCatalog = seriesCatalog,
        allPersonalizedWallRows = allPersonalizedWallRows,
        allStaticWallRows = allStaticWallRows,
        movieWallRows = movieWallRows,
        seriesWallRows = seriesWallRows,
        movieSidebarBundle = movieSidebarBundle,
        seriesSidebarBundle = seriesSidebarBundle,
        revision = revision
    )
}
