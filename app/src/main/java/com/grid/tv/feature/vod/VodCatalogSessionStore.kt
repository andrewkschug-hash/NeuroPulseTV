package com.grid.tv.feature.vod

import android.util.Log
import com.grid.tv.domain.model.VodBrowseRow
import com.grid.tv.domain.model.VodContentFilter
import com.grid.tv.domain.model.VodItem
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.domain.session.PlaylistContext
import com.grid.tv.player.LowEndDeviceMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class VodCatalogTrimSnapshot(
    val wallItemsDropped: Int,
    val browseRowsDropped: Int,
    val hubWasActive: Boolean,
    val activeContentFilter: String,
)

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

    @Volatile
    private var cachedPartitions: VodCatalogPartitions = VodCatalogPartitions.EMPTY

    @Volatile
    private var rawMovieBrowseRows: List<VodBrowseRow> = emptyList()

    @Volatile
    private var rawSeriesBrowseRows: List<VodBrowseRow> = emptyList()

    @Volatile
    private var hubActive: Boolean = false

    @Volatile
    private var activeContentFilter: VodContentFilter = VodContentFilter.ALL

    private val warmMutex = Mutex()
    private var shellWarmed = false

    fun setHubSessionState(active: Boolean, contentFilter: VodContentFilter = activeContentFilter) {
        hubActive = active
        activeContentFilter = contentFilter
    }

    fun trimForMemoryPressure(): VodCatalogTrimSnapshot {
        val filter = activeContentFilter
        val wasHubActive = hubActive
        val beforeItems = cachedPartitions.estimatedWallItemCount()
        val beforeBrowseRows = cachedPartitions.movieBrowseRows.size + cachedPartitions.seriesBrowseRows.size

        if (!wasHubActive) {
            clearPrefetchedShell()
            return VodCatalogTrimSnapshot(
                wallItemsDropped = beforeItems,
                browseRowsDropped = beforeBrowseRows + rawMovieBrowseRows.size + rawSeriesBrowseRows.size,
                hubWasActive = false,
                activeContentFilter = filter.name,
            )
        }

        val trimmed = cachedPartitions.trimForMemoryPressure(filter)
        val rawBrowseDropped = rawMovieBrowseRows.size + rawSeriesBrowseRows.size
        cachedPartitions = trimmed
        rawMovieBrowseRows = emptyList()
        rawSeriesBrowseRows = emptyList()
        cachedUiState = cachedUiState.copy(
            catalogPartitions = trimmed,
            wallRows = trimmed.wallRowsFor(filter),
            wallRowsRevision = trimmed.wallRowsRevisionFor(filter),
            movieBrowseRows = trimmed.movieBrowseRows,
            seriesBrowseRows = trimmed.seriesBrowseRows,
        )

        val afterItems = trimmed.estimatedWallItemCount()
        val afterBrowseRows = trimmed.movieBrowseRows.size + trimmed.seriesBrowseRows.size
        return VodCatalogTrimSnapshot(
            wallItemsDropped = (beforeItems - afterItems).coerceAtLeast(0),
            browseRowsDropped = (beforeBrowseRows - afterBrowseRows).coerceAtLeast(0) + rawBrowseDropped,
            hubWasActive = true,
            activeContentFilter = filter.name,
        )
    }

    private fun clearPrefetchedShell() {
        cachedPartitions = VodCatalogPartitions.EMPTY
        rawMovieBrowseRows = emptyList()
        rawSeriesBrowseRows = emptyList()
        cachedUiState = cachedUiState.copy(
            wallRows = emptyList(),
            movieBrowseRows = emptyList(),
            seriesBrowseRows = emptyList(),
            catalogPartitions = VodCatalogPartitions.EMPTY,
            hero = cachedUiState.hero.copy(featuredCarousel = emptyList()),
        )
        shellWarmed = false
    }

    fun cachedUiState(): VodUiState = cachedUiState

    fun cachedPartitions(): VodCatalogPartitions = cachedPartitions

    fun cachedMovieBrowseRows(): List<VodBrowseRow> = cachedUiState.movieBrowseRows

    fun cachedSeriesBrowseRows(): List<VodBrowseRow> = cachedUiState.seriesBrowseRows

    fun cachedRawMovieBrowseRows(): List<VodBrowseRow> = rawMovieBrowseRows

    fun cachedRawSeriesBrowseRows(): List<VodBrowseRow> = rawSeriesBrowseRows

    fun publishRawBrowseRows(
        movieBrowseRows: List<VodBrowseRow> = emptyList(),
        seriesBrowseRows: List<VodBrowseRow> = emptyList(),
    ) {
        if (movieBrowseRows.isNotEmpty()) rawMovieBrowseRows = movieBrowseRows
        if (seriesBrowseRows.isNotEmpty()) rawSeriesBrowseRows = seriesBrowseRows
    }

    fun publishPartitions(partitions: VodCatalogPartitions) {
        if (partitions == VodCatalogPartitions.EMPTY) return
        cachedPartitions = partitions
    }

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
        publishRawBrowseRows(movieBrowseRows, seriesBrowseRows)
        val partitions = buildVodCatalogPartitions(
            VodCatalogPartitionInputs(
                movieBrowseRows = movieBrowseRows,
                seriesBrowseRows = seriesBrowseRows,
                movieCategories = emptyList(),
                seriesCategories = emptyList(),
                continueWatching = emptyList(),
                trendingMovies = emptyList(),
                recommendedMovies = emptyList()
            ),
            prefetchTabWallRows = !LowEndDeviceMode.isEnabled(),
        )
        publishPartitions(partitions)
        val wallRows = partitions.wallRowsFor(VodContentFilter.ALL)
        val featured = movieBrowseRows
            .flatMap { row -> row.movies }
            .take(8)
        cachedUiState = cachedUiState.copy(
            wallRows = wallRows,
            wallRowsRevision = partitions.wallRowsRevisionFor(VodContentFilter.ALL),
            movieBrowseRows = movieBrowseRows,
            seriesBrowseRows = seriesBrowseRows,
            catalogPartitions = partitions,
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
