package com.grid.tv.domain.model

enum class PlaylistType { M3U, XTREAM, STALKER }

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
    val duration: String?,
    /** Primary Xtream category id (string-normalized). */
    val categoryId: String? = null,
    /**
     * Full membership list from `category_id` + `category_ids` (all string-normalized).
     * [categoryId] is always [categoryIds].firstOrNull().
     */
    val categoryIds: List<String> = emptyList(),
    val addedEpochSec: Long? = null,
    val playlistId: Long = 0L
)

data class VodCategory(
    val id: String,
    val name: String,
    val playlistId: Long = 0L
)

data class SeriesShow(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val categoryId: String? = null,
    /** Full membership from singular + plural Xtream category fields (string-normalized). */
    val categoryIds: List<String> = emptyList(),
    val genre: String? = null,
    val plot: String? = null,
    val playlistId: Long = 0L
)

data class SeriesDetail(
    val seasons: List<SeriesSeason> = emptyList(),
    val plot: String? = null
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
    val duration: String?,
    val episodeNumber: Int? = null
)
