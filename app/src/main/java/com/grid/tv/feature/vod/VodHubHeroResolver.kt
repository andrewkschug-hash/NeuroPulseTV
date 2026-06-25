package com.grid.tv.feature.vod

import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import com.grid.tv.domain.model.VodItem

/** Resolves volatile hero carousel selection from stable content + index. */
object VodHubHeroResolver {
    fun itemAt(carousel: List<VodItem>, index: Int): VodItem? {
        if (carousel.isEmpty()) return null
        return carousel[index.coerceIn(0, carousel.lastIndex)]
    }

    fun enrichmentFor(
        item: VodItem?,
        enrichmentMap: Map<String, TitleEnrichmentEntity>
    ): TitleEnrichmentEntity? {
        if (item == null || item.playlistId <= 0L) return null
        return enrichmentMap[TitleEnrichmentRepository.xtreamVodKey(item.playlistId, item.streamId)]
    }
}
