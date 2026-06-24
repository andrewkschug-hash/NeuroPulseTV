package com.grid.tv.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.grid.tv.domain.model.FavoriteGroup
import com.grid.tv.feature.epg.GuideChannelFilter

/** Non-grid EPG screen state — excludes playback, scanner badges, and clock ticks. */
@Immutable
data class HomeEpgChromeSnapshot(
    val isInitializing: Boolean,
    val hasConnection: Boolean,
    val guideFilter: GuideChannelFilter,
    val favoriteGroupFilter: Long?,
    val guideFiltersConfigured: Boolean,
    val guideSettingsLoaded: Boolean,
    val isReloadingChannels: Boolean,
    val channelGroups: List<String>,
    val groupChannelCounts: Map<String, Int>,
    val hasCatalogChannels: Boolean,
    val demoFavoriteIds: Set<Long>,
    val favoriteGroups: List<FavoriteGroup>,
    val favoriteSavedMessage: String?,
    val guidePreviewEnabled: Boolean,
    val guidePreviewChannelId: Long?,
    val guidePosition: EpgGuidePosition,
    val vodProgress: Map<Pair<Long, Long>, Long>
) {
    companion object {
        val INITIAL = HomeEpgChromeSnapshot(
            isInitializing = true,
            hasConnection = false,
            guideFilter = GuideChannelFilter.All,
            favoriteGroupFilter = null,
            guideFiltersConfigured = false,
            guideSettingsLoaded = false,
            isReloadingChannels = false,
            channelGroups = emptyList(),
            groupChannelCounts = emptyMap(),
            hasCatalogChannels = false,
            demoFavoriteIds = emptySet(),
            favoriteGroups = emptyList(),
            favoriteSavedMessage = null,
            guidePreviewEnabled = false,
            guidePreviewChannelId = null,
            guidePosition = EpgGuidePosition(),
            vodProgress = emptyMap()
        )
    }
}
