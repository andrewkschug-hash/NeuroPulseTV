package com.grid.tv.domain.model

data class SearchResultItem(
    val id: String,
    val primaryTitle: String,
    val secondaryLine: String,
    val imageUrl: String?,
    val type: SearchResultType,
    val channelId: Long? = null,
    val program: Program? = null,
    val vodItem: VodItem? = null,
    val seriesShow: SeriesShow? = null,
    val seriesEpisode: SeriesEpisode? = null,
    val seriesSeasonNumber: Int? = null,
    val actorName: String? = null,
    val genreName: String? = null,
    val isLive: Boolean = false
)

enum class SearchResultType {
    CHANNEL,
    PROGRAM,
    VOD,
    SERIES,
    EPISODE,
    ACTOR,
    GENRE
}
