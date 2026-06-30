package com.grid.tv.feature.enrichment

import com.grid.tv.domain.model.ContinueWatchingItem
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem
import com.grid.tv.player.LowEndDeviceMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class VisibleRowEnrichmentPrefetcher @Inject constructor(
    private val titleEnrichmentRepository: TitleEnrichmentRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun prefetchVodItems(items: List<VodItem>, maxItems: Int = defaultMaxItems()) {
        if (items.isEmpty()) return
        scope.launch {
            val slice = items.take(maxItems)
            val keys = slice.map { TitleEnrichmentRepository.xtreamVodKey(it.playlistId, it.streamId) }
            titleEnrichmentRepository.getCachedBatch(keys)
            slice.forEach { item ->
                titleEnrichmentRepository.enrichOnDemand(
                    providerKey = TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId),
                    title = item.title,
                    isTv = false
                )
            }
        }
    }

    fun prefetchSeriesShows(shows: List<SeriesShow>, maxItems: Int = defaultMaxItems()) {
        if (shows.isEmpty()) return
        scope.launch {
            val slice = shows.take(maxItems)
            val keys = slice.map { TitleEnrichmentRepository.xtreamSeriesKey(it.playlistId, it.id) }
            titleEnrichmentRepository.getCachedBatch(keys)
            slice.forEach { show ->
                titleEnrichmentRepository.enrichOnDemand(
                    providerKey = TitleEnrichmentRepository.xtreamSeriesKey(show.playlistId, show.id),
                    title = show.name,
                    isTv = true
                )
            }
        }
    }

    fun prefetchContinueWatching(items: List<ContinueWatchingItem>, maxItems: Int = defaultMaxItems()) {
        if (items.isEmpty()) return
        scope.launch {
            items.take(maxItems).forEach { item ->
                titleEnrichmentRepository.enrichContinueWatching(item)
            }
        }
    }

    fun prefetchSearchResults(items: List<com.grid.tv.domain.model.SearchResultItem>, maxItems: Int = defaultMaxItems()) {
        if (items.isEmpty()) return
        scope.launch {
            val slice = items
                .filter { it.vodItem != null || it.seriesShow != null }
                .take(maxItems)
            slice.forEach { result ->
                result.vodItem?.let { item ->
                    titleEnrichmentRepository.enrichOnDemand(
                        providerKey = TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId),
                        title = item.title,
                        isTv = false,
                    )
                }
                result.seriesShow?.let { show ->
                    titleEnrichmentRepository.enrichOnDemand(
                        providerKey = TitleEnrichmentRepository.xtreamSeriesKey(show.playlistId, show.id),
                        title = show.name,
                        isTv = true,
                    )
                }
            }
        }
    }

    private fun defaultMaxItems(): Int =
        if (LowEndDeviceMode.current().active) 20 else 40
}
