package com.grid.tv.domain.model

/**
 * Shared search-surface predicates used by unified overlay, VOD inline, and channel browser.
 */
object SearchSurfaceLogic {
    fun shouldShowResultRows(
        query: String,
        isSearching: Boolean,
        lastCompletedQuery: String,
        inFlightQuery: String,
        channelsReady: Boolean = false,
        vodReady: Boolean = false,
        seriesReady: Boolean = false,
        hasAnyResults: Boolean = false,
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (!isSearching && trimmed == lastCompletedQuery.trim()) return true
        if (isSearching && trimmed == inFlightQuery.trim()) {
            if (trimmed != lastCompletedQuery.trim() &&
                !channelsReady && !vodReady && !seriesReady && !hasAnyResults
            ) {
                return false
            }
            return true
        }
        return false
    }

    fun shouldShowNoResults(
        query: String,
        isSearching: Boolean,
        lastCompletedQuery: String,
        inFlightQuery: String,
        channelsReady: Boolean,
        vodReady: Boolean,
        seriesReady: Boolean,
        isEmpty: Boolean,
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        if (isSearching) return false
        if (trimmed != lastCompletedQuery.trim() && trimmed != inFlightQuery.trim()) return false
        if (!channelsReady || !vodReady || !seriesReady) return false
        return isEmpty
    }

    fun shouldShowSearching(query: String, isSearching: Boolean): Boolean =
        isSearching && query.isNotBlank()

    /** Paging-based search surfaces (VOD inline, channel browser). */
    fun pagedSearchState(
        query: String,
        isRefreshLoading: Boolean,
        itemCount: Int,
        lastCompletedQuery: String = "",
    ): SearchUiState.Active {
        val trimmed = query.trim()
        val inFlight = if (isRefreshLoading && trimmed.isNotEmpty()) trimmed else ""
        val completed = if (!isRefreshLoading && trimmed.isNotEmpty()) trimmed else lastCompletedQuery
        val ready = !isRefreshLoading || trimmed.isEmpty()
        return SearchUiState.Active(
            query = query,
            isSearching = isRefreshLoading && trimmed.isNotEmpty(),
            results = UnifiedSearchResults(),
            searchGeneration = 0L,
            lastCompletedQuery = completed,
            inFlightQuery = inFlight,
            channelsReady = ready,
            vodReady = ready,
            seriesReady = ready,
            hasAnyResults = itemCount > 0,
        )
    }
}
