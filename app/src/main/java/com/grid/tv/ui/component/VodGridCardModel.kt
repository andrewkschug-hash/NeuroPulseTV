package com.grid.tv.ui.component

import com.grid.tv.domain.model.SeriesShow
import com.grid.tv.domain.model.VodItem

data class VodGridCardModel(
  val key: String,
  val title: String,
  val group: String?,
  val posterUrl: String?,
  val streamUrl: String,
  val streamId: Long,
  val playlistId: Long,
  val showId: Long = 0L,
  val showHdBadge: Boolean = false
)

fun VodItem.toGridCardModel(): VodGridCardModel = VodGridCardModel(
  key = "${playlistId}_${streamId}",
  title = title,
  group = categoryId,
  posterUrl = posterUrl,
  streamUrl = streamUrl,
  streamId = streamId,
  playlistId = playlistId,
  showHdBadge = showsHdBadge()
)

fun SeriesShow.toGridCardModel(): VodGridCardModel = VodGridCardModel(
  key = "${playlistId}_${id}",
  title = name,
  group = categoryId,
  posterUrl = coverUrl,
  streamUrl = "",
  streamId = id,
  playlistId = playlistId,
  showId = id
)
