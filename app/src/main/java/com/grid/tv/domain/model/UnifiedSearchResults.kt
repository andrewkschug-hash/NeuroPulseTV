package com.grid.tv.domain.model

data class UnifiedSearchResults(
    val query: String = "",
    val channels: List<SearchResultItem> = emptyList(),
    val movies: List<SearchResultItem> = emptyList(),
    val series: List<SearchResultItem> = emptyList(),
    val episodes: List<SearchResultItem> = emptyList(),
    val actors: List<SearchResultItem> = emptyList(),
    val genres: List<SearchResultItem> = emptyList(),
    val programs: List<SearchResultItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val trendingSearches: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() &&
            episodes.isEmpty() && actors.isEmpty() && genres.isEmpty() && programs.isEmpty()

    val flatResults: List<SearchResultItem>
        get() = channels + movies + series + episodes + actors + genres + programs
}
