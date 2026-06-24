package com.grid.tv.domain.model

enum class VodContentFilter {
    ALL,
    MOVIES,
    SERIES,
    /** VOD-only inline search (movies + series); not global Live TV search. */
    SEARCH
}

sealed class VodWallItem {
    abstract val key: String

    data class MovieItem(val movie: VodItem) : VodWallItem() {
        override val key: String = "movie_${movie.playlistId}_${movie.streamId}"
    }

    data class SeriesItem(val show: SeriesShow) : VodWallItem() {
        override val key: String = "series_${show.playlistId}_${show.id}"
    }

    data class ContinueItem(val item: ContinueWatchingItem) : VodWallItem() {
        override val key: String = "cw_${item.contentKey}"
    }
}

data class VodWallRow(
    val id: String,
    val title: String,
    val items: List<VodWallItem>
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

fun genreIntegratedRowTitle(rowId: String, title: String): String = when (rowId) {
    "recent" -> "New Releases"
    "top_imdb" -> "Top Rated"
    "4k" -> "4K Ultra HD"
    "trending" -> "Trending"
    "continue_watching" -> "Continue Watching"
    "recommended" -> "Recommended For You"
    "all" -> sanitizeVodRowHeading(title)
    else -> sanitizeVodRowHeading(title)
}

private fun sanitizeVodRowHeading(raw: String): String {
    var clean = raw.trim()
        .removePrefix("Top ")
        .removePrefix("Popular ")
        .replace(Regex("""^(?:Top|Popular)\s+\d+\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+\d+$"""), "")
        .replace(Regex("""^\d+\s*[-:]?\s*"""), "")
        .trim()
    if (clean.endsWith(" Series", ignoreCase = true)) {
        clean = clean.dropLast(" Series".length).trim()
    }
    return clean.ifBlank { raw.trim() }
}

fun buildVodWallRows(
    filter: VodContentFilter,
    continueWatching: List<ContinueWatchingItem>,
    trendingMovies: List<VodItem>,
    recommendedMovies: List<VodItem>,
    movieBrowseRows: List<VodBrowseRow>,
    seriesBrowseRows: List<VodBrowseRow>
): List<VodWallRow> {
    val rows = mutableListOf<VodWallRow>()

    if (filter != VodContentFilter.SERIES && continueWatching.isNotEmpty()) {
        rows += VodWallRow(
            id = "continue_watching",
            title = "Continue Watching",
            items = continueWatching.map { VodWallItem.ContinueItem(it) }
        )
    }

    if (filter == VodContentFilter.ALL) {
        val trendingItems = buildList {
            trendingMovies.take(12).forEach { add(VodWallItem.MovieItem(it)) }
            seriesBrowseRows.firstOrNull()?.series?.take(12)?.forEach { add(VodWallItem.SeriesItem(it)) }
        }.distinctBy { it.key }
        if (trendingItems.isNotEmpty()) {
            rows += VodWallRow("trending", "Trending: Movies & Series", trendingItems)
        }

        recommendedMovies.take(20).map { VodWallItem.MovieItem(it) }.takeIf { it.isNotEmpty() }?.let { items ->
            rows += VodWallRow("recommended", "Recommended For You", items)
        }

        val movieGenreRows = movieBrowseRows
            .filter { it.id !in setOf("recent", "top_imdb", "4k") && it.movies.isNotEmpty() }
            .take(8)
        val seriesGenreRows = seriesBrowseRows
            .filter { it.id !in setOf("all", "4k") && it.series.isNotEmpty() }
            .take(8)

        val maxPairs = maxOf(movieGenreRows.size, seriesGenreRows.size)
        for (index in 0 until maxPairs) {
            movieGenreRows.getOrNull(index)?.let { browseRow ->
                rows += VodWallRow(
                    id = "movie_${browseRow.id}",
                    title = genreIntegratedRowTitle(browseRow.id, browseRow.title),
                    items = browseRow.movies.map { VodWallItem.MovieItem(it) }
                )
            }
            seriesGenreRows.getOrNull(index)?.let { browseRow ->
                rows += VodWallRow(
                    id = "series_${browseRow.id}",
                    title = genreIntegratedRowTitle(browseRow.id, browseRow.title),
                    items = browseRow.series.map { VodWallItem.SeriesItem(it) }
                )
            }
        }

        movieBrowseRows.filter { it.id in setOf("recent", "top_imdb", "4k") && it.movies.isNotEmpty() }
            .forEach { browseRow ->
                rows += VodWallRow(
                    id = "movie_${browseRow.id}",
                    title = genreIntegratedRowTitle(browseRow.id, browseRow.title),
                    items = browseRow.movies.map { VodWallItem.MovieItem(it) }
                )
            }
    } else if (filter == VodContentFilter.MOVIES) {
        movieBrowseRows.filter { !it.isEmpty }.forEach { browseRow ->
            rows += VodWallRow(
                id = "movie_${browseRow.id}",
                title = genreIntegratedRowTitle(browseRow.id, browseRow.title),
                items = browseRow.movies.map { VodWallItem.MovieItem(it) }
            )
        }
    } else {
        seriesBrowseRows.filter { !it.isEmpty }.forEach { browseRow ->
            rows += VodWallRow(
                id = "series_${browseRow.id}",
                title = genreIntegratedRowTitle(browseRow.id, browseRow.title),
                items = browseRow.series.map { VodWallItem.SeriesItem(it) }
            )
        }
    }

    return rows.filter { !it.isEmpty }.distinctBy { it.id }
}
