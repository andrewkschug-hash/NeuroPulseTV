package com.grid.tv.data.db.mapper

import com.grid.tv.data.db.entity.SeriesShowEntity
import com.grid.tv.data.db.entity.VodCategoryEntity
import com.grid.tv.data.db.entity.VodStreamEntity
import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodCategory
import com.grid.tv.domain.model.VodItem

fun VodItem.toEntity(): VodStreamEntity = VodStreamEntity(
    playlistId = playlistId,
    streamId = streamId,
    title = title,
    streamUrl = streamUrl,
    posterUrl = posterUrl,
    plot = plot,
    cast = cast,
    director = director,
    genre = genre,
    rating = rating,
    duration = duration,
    categoryId = categoryId,
    addedEpochSec = addedEpochSec
)

fun VodStreamEntity.toDomain(): VodItem = VodItem(
    id = streamId,
    title = title,
    streamId = streamId,
    streamUrl = streamUrl,
    posterUrl = posterUrl,
    plot = plot,
    cast = cast,
    director = director,
    genre = genre,
    rating = rating,
    duration = duration,
    categoryId = categoryId,
    addedEpochSec = addedEpochSec,
    playlistId = playlistId
)

fun VodCategory.toEntity(): VodCategoryEntity = VodCategoryEntity(
    playlistId = playlistId,
    categoryId = id,
    name = name
)

fun VodCategoryEntity.toDomain(): VodCategory = VodCategory(
    id = categoryId,
    name = name,
    playlistId = playlistId
)

fun SeriesShow.toEntity(): SeriesShowEntity = SeriesShowEntity(
    playlistId = playlistId,
    seriesId = id,
    name = name,
    coverUrl = coverUrl,
    categoryId = categoryId,
    genre = genre
)

fun SeriesShowEntity.toDomain(): SeriesShow = SeriesShow(
    id = seriesId,
    name = name,
    coverUrl = coverUrl,
    categoryId = categoryId,
    genre = genre,
    playlistId = playlistId
)
