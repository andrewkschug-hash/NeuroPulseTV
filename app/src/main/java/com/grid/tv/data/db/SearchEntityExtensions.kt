package com.grid.tv.data.db

import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.SeriesShowEntity
import com.grid.tv.data.db.entity.VodStreamEntity
import com.grid.tv.feature.search.SearchTitleNormalizer

fun ChannelEntity.withSearchTitle(): ChannelEntity =
    copy(searchTitle = SearchTitleNormalizer.normalize(name))

fun VodStreamEntity.withSearchTitle(): VodStreamEntity =
    copy(searchTitle = SearchTitleNormalizer.normalize(title))

fun SeriesShowEntity.withSearchTitle(): SeriesShowEntity =
    copy(searchTitle = SearchTitleNormalizer.normalize(name))
