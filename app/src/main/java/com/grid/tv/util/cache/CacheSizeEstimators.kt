package com.grid.tv.util.cache

import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.SeriesDetail

object CacheSizeEstimators {
    fun titleEnrichment(entity: TitleEnrichmentEntity): Int {
        var bytes = 256
        bytes += entity.providerKey.length * 2
        bytes += (entity.title?.length ?: 0) * 2
        bytes += (entity.overview?.length ?: 0) * 2
        bytes += (entity.cast?.length ?: 0) * 2
        bytes += (entity.posterUrl?.length ?: 0) * 2
        bytes += (entity.backdropUrl?.length ?: 0) * 2
        bytes += (entity.contentVector?.length ?: 0) * 2
        return bytes.coerceAtLeast(BoundedMemoryCache.DEFAULT_STRING_ENTRY_BYTES)
    }

    fun programList(programs: List<Program>): Int =
        (programs.size * 280 + 64).coerceAtLeast(BoundedMemoryCache.DEFAULT_STRING_ENTRY_BYTES)

    fun seriesDetail(detail: SeriesDetail): Int {
        val episodeCount = detail.seasons.sumOf { it.episodes.size }
        return (episodeCount * 320 + detail.seasons.size * 128 + 512)
            .coerceAtLeast(BoundedMemoryCache.DEFAULT_STRING_ENTRY_BYTES)
    }
}
