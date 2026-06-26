package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.model.buildVodWallRows
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.domain.session.PlaylistContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide VOD shell cache so the hub can paint browse rows and wall content
 * before [VodHubViewModel] finishes wiring repository flows.
 */
@Singleton
class VodCatalogSessionStore @Inject constructor(
    private val playlistContext: PlaylistContext
) {
    @Volatile
    private var cachedUiState: VodUiState = VodUiState()

    private val warmMutex = Mutex()
    private var shellWarmed = false

    fun cachedUiState(): VodUiState = cachedUiState

    fun cachedMovieBrowseRows(): List<VodBrowseRow> = cachedUiState.movieBrowseRows

    fun cachedSeriesBrowseRows(): List<VodBrowseRow> = cachedUiState.seriesBrowseRows

    fun cachedFeaturedCarousel(): List<VodItem> = cachedUiState.hero.featuredCarousel

    fun hasInstantShell(): Boolean =
        cachedUiState.wallRows.isNotEmpty() ||
            cachedUiState.movieBrowseRows.isNotEmpty() ||
            cachedUiState.seriesBrowseRows.isNotEmpty()

    fun publishUiState(state: VodUiState) {
        if (
            state.wallRows.isEmpty() &&
            state.movieBrowseRows.isEmpty() &&
            state.seriesBrowseRows.isEmpty()
        ) {
            return
        }
        cachedUiState = state
    }

    suspend fun warmShell(repository: IptvRepository) {
        if (shellWarmed && hasInstantShell()) return
        warmMutex.withLock {
            if (shellWarmed && hasInstantShell()) return
            shellWarmed = true
            withContext(Dispatchers.IO) {
                runCatching { warmShellInternal(repository) }
                    .onFailure { error ->
                        Log.w(TAG, "VOD shell warm failed: ${error.message}", error)
                    }
            }
        }
    }

    private suspend fun warmShellInternal(repository: IptvRepository) {
        val movieCount = repository.vodStreamCount().first()
        val seriesCount = repository.seriesShowCount().first()
        if (movieCount <= 0 && seriesCount <= 0) return

        val playlistId = playlistContext.resolveOrNull(null)
        val movieBrowseRows = if (movieCount > 0) {
            repository.loadMovieBrowseRows(playlistId = playlistId)
        } else {
            emptyList()
        }
        val seriesBrowseRows = if (seriesCount > 0) {
            repository.loadSeriesBrowseRows()
        } else {
            emptyList()
        }
        val wallRows = buildVodWallRows(
            filter = VodContentFilter.ALL,
            continueWatching = emptyList(),
            trendingMovies = emptyList(),
            recommendedMovies = emptyList(),
            movieBrowseRows = movieBrowseRows,
            seriesBrowseRows = seriesBrowseRows
        )
        val featured = movieBrowseRows
            .flatMap { row -> row.movies }
            .take(8)
        cachedUiState = cachedUiState.copy(
            wallRows = wallRows,
            wallRowsRevision = wallRows.joinToString("|") { "${it.id}:${it.items.size}" },
            movieBrowseRows = movieBrowseRows,
            seriesBrowseRows = seriesBrowseRows,
            hero = cachedUiState.hero.copy(featuredCarousel = featured)
        )
        Log.i(
            TAG,
            "VOD shell warmed rows=${wallRows.size} movies=${movieBrowseRows.size} series=${seriesBrowseRows.size}"
        )
    }

    companion object {
        private const val TAG = "VodCatalogSession"
    }
}
