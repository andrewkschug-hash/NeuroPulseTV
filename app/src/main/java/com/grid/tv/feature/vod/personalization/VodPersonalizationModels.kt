package com.grid.tv.feature.vod.personalization

enum class VodContentType {
    MOVIE, SERIES, EPISODE;

    companion object {
        fun fromStored(value: String): VodContentType = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: MOVIE
    }
}

enum class VodNotificationType(val displayTitle: String) {
    NEW_EPISODE("New Episode Available"),
    NEW_SEASON("New Season Available"),
    SERIES_RETURNING("Series Returning");

    companion object {
        fun fromStored(value: String): VodNotificationType = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: NEW_EPISODE
    }
}

enum class ForYouFeedSection {
    CONTINUE_WATCHING,
    NEW_FOR_YOU,
    RECENTLY_ADDED_FOLLOWED,
    TRENDING
}

data class VodWatchEvent(
    val profileId: Long,
    val contentId: String,
    val contentType: VodContentType,
    val seriesId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val progressPercent: Float,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatched: Long
)

data class SeriesFollow(
    val profileId: Long,
    val seriesId: Long,
    val seriesTitle: String,
    val playlistId: Long,
    val following: Boolean,
    val autoFollowed: Boolean,
    val followedAt: Long
)

data class CatalogEpisode(
    val playlistId: Long,
    val seriesId: Long,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: Long,
    val episodeTitle: String,
    val addedAt: Long
)

data class NewEpisodeDetection(
    val seriesId: Long,
    val seriesTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val contentKey: String,
    val previousWatchedEpisode: Int?
)

data class VodNotification(
    val id: Long = 0,
    val profileId: Long,
    val type: VodNotificationType,
    val seriesId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val seriesTitle: String,
    val episodeTitle: String?,
    val contentKey: String?,
    val createdAt: Long,
    val readAt: Long? = null,
    val pushPending: Boolean = true
)

data class ForYouFeedItem(
    val section: ForYouFeedSection,
    val title: String,
    val subtitle: String,
    val seriesId: Long?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val contentKey: String?,
    val rankScore: Double,
    val posterUrl: String? = null
)

data class ForYouFeed(
    val profileId: Long,
    val items: List<ForYouFeedItem>,
    val generatedAt: Long = System.currentTimeMillis()
)
