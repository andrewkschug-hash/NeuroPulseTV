package com.grid.tv.domain.model

data class VodCatalogProgress(
    val moviesLoaded: Int = 0,
    val moviesTotal: Int = 0,
    val seriesLoaded: Int = 0,
    val seriesTotal: Int = 0,
    val isLoading: Boolean = false,
    val moviesPhaseFinished: Boolean = false,
    val seriesPhaseFinished: Boolean = false
) {
    val isMoviesPhaseComplete: Boolean
        get() = moviesPhaseFinished || (moviesTotal > 0 && moviesLoaded >= moviesTotal)

    val isSeriesPhaseComplete: Boolean
        get() = seriesPhaseFinished || (seriesTotal > 0 && seriesLoaded >= seriesTotal)

    /** True once at least one series batch has landed (partial catalog is browsable). */
    val hasSeriesFirstBatch: Boolean
        get() = seriesLoaded > 0 || seriesPhaseFinished

    fun moviesProgressFraction(): Float =
        if (moviesTotal <= 0) {
            0f
        } else {
            (moviesLoaded.toFloat() / moviesTotal.toFloat()).coerceIn(0f, 1f)
        }

    fun seriesProgressFraction(): Float =
        if (seriesTotal <= 0) {
            0f
        } else {
            (seriesLoaded.toFloat() / seriesTotal.toFloat()).coerceIn(0f, 1f)
        }
}
