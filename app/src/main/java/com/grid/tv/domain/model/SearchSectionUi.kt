package com.grid.tv.domain.model

/**
 * Progressive search section model — each section hydrates independently.
 */
data class SearchSectionSnapshot(
    val channels: List<SearchResultItem> = emptyList(),
    val movies: List<SearchResultItem> = emptyList(),
    val series: List<SearchResultItem> = emptyList(),
    val episodes: List<SearchResultItem> = emptyList(),
    val actors: List<SearchResultItem> = emptyList(),
    val genres: List<SearchResultItem> = emptyList(),
    val programs: List<SearchResultItem> = emptyList(),
    val showChannelsSkeleton: Boolean = false,
    val showVodSkeleton: Boolean = false,
    val showSeriesSkeleton: Boolean = false,
) {
    val hasRenderableContent: Boolean
        get() = channels.isNotEmpty() ||
            movies.isNotEmpty() ||
            series.isNotEmpty() ||
            episodes.isNotEmpty() ||
            actors.isNotEmpty() ||
            genres.isNotEmpty() ||
            programs.isNotEmpty() ||
            showChannelsSkeleton ||
            showVodSkeleton ||
            showSeriesSkeleton

    val flatSelectableResults: List<SearchResultItem>
        get() = buildList {
            addAll(channels)
            addAll(movies)
            addAll(series)
            addAll(episodes)
            addAll(actors)
            addAll(genres)
            addAll(programs)
        }
}

object SearchSectionUi {
    const val CHANNEL_SKELETON_COUNT = 4
    const val VOD_SKELETON_COUNT = 6
    const val SERIES_SKELETON_COUNT = 6

    fun snapshot(state: SearchUiState): SearchSectionSnapshot {
        val trimmed = state.query.trim()
        if (trimmed.isEmpty()) return SearchSectionSnapshot()

        val showRows = state.shouldShowResultRows
        val searching = state.isSearching

        val showChannelsSkeleton = searching && (!showRows || !state.channelsReady)
        val showVodSkeleton = searching && showRows && state.channelsReady && !state.vodReady
        val showSeriesSkeleton = searching && showRows && state.vodReady && !state.seriesReady

        val results = if (showRows) state.results else UnifiedSearchResults()

        return SearchSectionSnapshot(
            channels = if (state.channelsReady && showRows) results.channels else emptyList(),
            movies = if (state.vodReady && showRows) results.movies else emptyList(),
            series = if (state.seriesReady && showRows) results.series else emptyList(),
            episodes = if (state.vodReady && showRows) results.episodes else emptyList(),
            actors = if (state.isSearchComplete && showRows) results.actors else emptyList(),
            genres = if (state.isSearchComplete && showRows) results.genres else emptyList(),
            programs = if (state.isSearchComplete && showRows) results.programs else emptyList(),
            showChannelsSkeleton = showChannelsSkeleton,
            showVodSkeleton = showVodSkeleton,
            showSeriesSkeleton = showSeriesSkeleton,
        )
    }
}
