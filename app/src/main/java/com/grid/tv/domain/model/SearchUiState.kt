package com.grid.tv.domain.model

/**
 * Single source of truth for unified search UI.
 * Never infer "no results" from [results] alone — use [shouldShowNoResults].
 */
sealed class SearchUiState {
    abstract val query: String
    abstract val isSearching: Boolean
    abstract val results: UnifiedSearchResults
    abstract val searchGeneration: Long
    abstract val lastCompletedQuery: String
    abstract val inFlightQuery: String
    abstract val channelsReady: Boolean
    abstract val vodReady: Boolean
    abstract val seriesReady: Boolean
    abstract val hasAnyResults: Boolean

    val flatResults: List<SearchResultItem>
        get() = results.flatResults

    /**
     * Results safe to render while the user is typing a new query.
     * Shows progressive partial rows for the in-flight query.
     */
    val displayResults: UnifiedSearchResults
        get() = if (shouldShowResultRows) results else UnifiedSearchResults(
            recentSearches = results.recentSearches,
            trendingSearches = results.trendingSearches,
        )

    val shouldShowResultRows: Boolean
        get() = SearchSurfaceLogic.shouldShowResultRows(
            query = query,
            isSearching = isSearching,
            lastCompletedQuery = lastCompletedQuery,
            inFlightQuery = inFlightQuery,
            channelsReady = channelsReady,
            vodReady = vodReady,
            seriesReady = seriesReady,
            hasAnyResults = hasAnyResults,
        )

    val shouldShowNoResults: Boolean
        get() = SearchSurfaceLogic.shouldShowNoResults(
            query = query,
            isSearching = isSearching,
            lastCompletedQuery = lastCompletedQuery,
            inFlightQuery = inFlightQuery,
            channelsReady = channelsReady,
            vodReady = vodReady,
            seriesReady = seriesReady,
            isEmpty = !hasAnyResults && results.isEmpty,
        )

    val shouldShowSearching: Boolean
        get() = SearchSurfaceLogic.shouldShowSearching(query, isSearching)

    val isSearchComplete: Boolean
        get() = !isSearching || (channelsReady && vodReady && seriesReady)

    data class Active(
        override val query: String = "",
        override val isSearching: Boolean = false,
        override val results: UnifiedSearchResults = UnifiedSearchResults(),
        override val searchGeneration: Long = 0L,
        override val lastCompletedQuery: String = "",
        override val inFlightQuery: String = "",
        override val channelsReady: Boolean = false,
        override val vodReady: Boolean = false,
        override val seriesReady: Boolean = false,
        override val hasAnyResults: Boolean = false,
    ) : SearchUiState()

    companion object {
        val Initial: SearchUiState = Active()
    }
}
