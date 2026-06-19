package com.grid.tv.domain.model

enum class VodCatalogEmptyReason {
    NONE,
    LOADING,
    NO_XTREAM_PLAYLIST,
    FETCH_FAILED,
    PARSE_FAILED,
    PARSE_ZERO,
    FILTERED_EMPTY,
    NOT_LOADED
}

data class VodCatalogStatus(
    val progress: VodCatalogProgress = VodCatalogProgress(),
    val moviesError: String? = null,
    val seriesError: String? = null,
    val moviesRawLength: Int = 0,
    val moviesParsedCount: Int = 0,
    val seriesRawLength: Int = 0,
    val seriesParsedCount: Int = 0,
    val hasXtreamPlaylist: Boolean = true
) {
    fun moviesEmptyReason(filteredCount: Int, catalogTotal: Int, categoryId: String?, searchQuery: String): VodCatalogEmptyReason {
        if (progress.isLoading && !progress.moviesPhaseFinished) return VodCatalogEmptyReason.LOADING
        if (!hasXtreamPlaylist) return VodCatalogEmptyReason.NO_XTREAM_PLAYLIST
        if (moviesError != null) return VodCatalogEmptyReason.FETCH_FAILED
        if (moviesRawLength > 0 && moviesParsedCount == 0) return VodCatalogEmptyReason.PARSE_FAILED
        if (progress.moviesPhaseFinished && catalogTotal == 0) return VodCatalogEmptyReason.PARSE_ZERO
        if (catalogTotal > 0 && filteredCount == 0) return VodCatalogEmptyReason.FILTERED_EMPTY
        if (!progress.moviesPhaseFinished && catalogTotal == 0) return VodCatalogEmptyReason.NOT_LOADED
        return VodCatalogEmptyReason.NONE
    }

    fun seriesEmptyReason(filteredCount: Int, catalogTotal: Int, category: String, searchQuery: String): VodCatalogEmptyReason {
        if (progress.isLoading && progress.moviesPhaseFinished && !progress.seriesPhaseFinished) {
            return VodCatalogEmptyReason.LOADING
        }
        if (!hasXtreamPlaylist) return VodCatalogEmptyReason.NO_XTREAM_PLAYLIST
        if (seriesError != null) return VodCatalogEmptyReason.FETCH_FAILED
        if (seriesRawLength > 0 && seriesParsedCount == 0) return VodCatalogEmptyReason.PARSE_FAILED
        if (progress.seriesPhaseFinished && catalogTotal == 0) return VodCatalogEmptyReason.PARSE_ZERO
        if (catalogTotal > 0 && filteredCount == 0) return VodCatalogEmptyReason.FILTERED_EMPTY
        if (!progress.seriesPhaseFinished && catalogTotal == 0) return VodCatalogEmptyReason.NOT_LOADED
        return VodCatalogEmptyReason.NONE
    }
}

fun VodCatalogEmptyReason.vodEmptyTitle(isMovies: Boolean): String = when (this) {
    VodCatalogEmptyReason.LOADING, VodCatalogEmptyReason.NOT_LOADED ->
        if (isMovies) "Loading movies" else "Loading series"
    VodCatalogEmptyReason.NO_XTREAM_PLAYLIST ->
        if (isMovies) "Movies unavailable" else "Series unavailable"
    VodCatalogEmptyReason.FETCH_FAILED ->
        if (isMovies) "Could not load movies" else "Could not load series"
    VodCatalogEmptyReason.PARSE_FAILED ->
        if (isMovies) "Could not parse movies" else "Could not parse series"
    VodCatalogEmptyReason.PARSE_ZERO ->
        if (isMovies) "No movies available" else "No series available"
    VodCatalogEmptyReason.FILTERED_EMPTY ->
        if (isMovies) "No movies match" else "No series match"
    VodCatalogEmptyReason.NONE ->
        if (isMovies) "No movies available" else "No series available"
}

fun VodCatalogEmptyReason.vodEmptyMessage(status: VodCatalogStatus, isMovies: Boolean): String = when (this) {
    VodCatalogEmptyReason.LOADING, VodCatalogEmptyReason.NOT_LOADED ->
        "Fetching your provider's catalog. Large libraries can take a minute."
    VodCatalogEmptyReason.NO_XTREAM_PLAYLIST ->
        "Connect an Xtream playlist in Settings to browse on-demand titles."
    VodCatalogEmptyReason.FETCH_FAILED -> {
        val detail = if (isMovies) status.moviesError else status.seriesError
        detail ?: "Network or server error while contacting your provider."
    }
    VodCatalogEmptyReason.PARSE_FAILED -> {
        val raw = if (isMovies) status.moviesRawLength else status.seriesRawLength
        "Received $raw bytes from the provider but parsed 0 entries. The response format may have changed."
    }
    VodCatalogEmptyReason.PARSE_ZERO ->
        if (isMovies) {
            "Your provider returned an empty movie catalog."
        } else {
            "Your provider returned an empty series catalog."
        }
    VodCatalogEmptyReason.FILTERED_EMPTY ->
        "Try another category or clear your search filter."
    VodCatalogEmptyReason.NONE -> ""
}
