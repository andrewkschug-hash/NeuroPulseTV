package com.neuropulse.tv.domain.model

enum class PlaylistType { M3U, XTREAM }

data class XtreamAccountInfo(
    val playlistId: Long,
    val playlistName: String,
    val status: String,
    val expiryDateEpochSec: Long?,
    val maxConnections: Int?,
    val serverUrl: String
)

data class VodItem(
    val id: Long,
    val title: String,
    val streamId: Long,
    val streamUrl: String,
    val posterUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val rating: String?,
    val duration: String?
)

data class SeriesShow(
    val id: Long,
    val name: String,
    val coverUrl: String?
)

data class SeriesSeason(
    val number: Int,
    val episodes: List<SeriesEpisode>
)

data class SeriesEpisode(
    val id: Long,
    val title: String,
    val extension: String,
    val streamUrl: String,
    val plot: String?,
    val duration: String?
)
