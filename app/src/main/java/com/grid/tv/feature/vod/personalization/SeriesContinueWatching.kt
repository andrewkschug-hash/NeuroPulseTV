package com.grid.tv.feature.vod.personalization

import com.grid.tv.domain.model.ContinueWatchingContentType
import com.grid.tv.domain.model.ContinueWatchingItem

internal fun matchContinueWatchingSeries(
    items: List<ContinueWatchingItem>,
    seriesId: Long,
    playlistId: Long = 0L
): ContinueWatchingItem? =
    items.filter {
        it.contentType == ContinueWatchingContentType.SERIES &&
            it.seriesId == seriesId &&
            (playlistId <= 0L || it.playlistId == playlistId)
    }.maxByOrNull { it.lastWatchedAt }
