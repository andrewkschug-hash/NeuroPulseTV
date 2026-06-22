package com.grid.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.grid.tv.data.db.dao.ContinueWatchingDao
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.ChannelScanDao
import com.grid.tv.data.db.dao.CanonicalChannelDao
import com.grid.tv.data.db.dao.EpgAliasHitDao
import com.grid.tv.data.db.dao.EpgLearnedMappingDao
import com.grid.tv.data.db.dao.EpgMatchAnalyticsDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.FavoriteDao
import com.grid.tv.data.db.dao.FavoriteGroupDao
import com.grid.tv.data.db.dao.PlaylistDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.ProfileFavoriteDao
import com.grid.tv.data.db.dao.ProfileSettingsDao
import com.grid.tv.data.db.dao.ProfileTasteGenomeDao
import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.ProgramDao
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.RecordingDao
import com.grid.tv.data.db.dao.ScheduledRecordingDao
import com.grid.tv.data.db.dao.SeriesRecordingRuleDao
import com.grid.tv.data.db.dao.PlaybackSessionTelemetryDao
import com.grid.tv.data.db.dao.StreamSourceHealthDao
import com.grid.tv.data.db.dao.ChannelHealthAggregateDao
import com.grid.tv.data.db.dao.ProviderHealthAggregateDao
import com.grid.tv.data.db.dao.VodWatchEventDao
import com.grid.tv.data.db.dao.SeriesFollowDao
import com.grid.tv.data.db.dao.VodCatalogEpisodeDao
import com.grid.tv.data.db.dao.FeaturedCurationDao
import com.grid.tv.data.db.dao.VodCategoryDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.dao.SeriesCategoryDao
import com.grid.tv.data.db.dao.SeriesShowDao
import com.grid.tv.data.db.dao.VodUserNotificationDao
import com.grid.tv.data.db.dao.StreamFailoverStatsDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.dao.SubtitleCacheDao
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.data.db.dao.MovieDetailsDao
import com.grid.tv.data.db.dao.WatchHistoryDao
import com.grid.tv.data.db.entity.ContinueWatchingEntity
import com.grid.tv.data.db.entity.ActiveProfileEntity
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.ChannelScanEntity
import com.grid.tv.data.db.entity.FavoriteEntity
import com.grid.tv.data.db.entity.FavoriteGroupEntity
import com.grid.tv.data.db.entity.FeaturedBannerStatsEntity
import com.grid.tv.data.db.entity.ProfileGenreAffinityEntity
import com.grid.tv.data.db.entity.PlaylistEntity
import com.grid.tv.data.db.entity.ProfileFavoriteEntity
import com.grid.tv.data.db.entity.ProfileSettingsEntity
import com.grid.tv.data.db.entity.ProfileWatchHistoryEntity
import com.grid.tv.data.db.entity.ProgramEntity
import com.grid.tv.data.db.entity.RecordedMediaEntity
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import com.grid.tv.data.db.entity.CanonicalChannelEntity
import com.grid.tv.data.db.entity.EpgAliasHitEntity
import com.grid.tv.data.db.entity.EpgLearnedMappingEntity
import com.grid.tv.data.db.entity.EpgMatchAnalyticsEntity
import com.grid.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.grid.tv.data.db.entity.RecordingEntity
import com.grid.tv.data.db.entity.ScheduledRecordingEntity
import com.grid.tv.data.db.entity.SeriesRecordingRuleEntity
import com.grid.tv.data.db.entity.PlaybackSessionTelemetryEntity
import com.grid.tv.data.db.entity.StreamSourceHealthEntity
import com.grid.tv.data.db.entity.ChannelHealthAggregateEntity
import com.grid.tv.data.db.entity.ProviderHealthAggregateEntity
import com.grid.tv.data.db.entity.VodWatchEventEntity
import com.grid.tv.data.db.entity.SeriesFollowEntity
import com.grid.tv.data.db.entity.VodCatalogEpisodeEntity
import com.grid.tv.data.db.entity.VodCategoryEntity
import com.grid.tv.data.db.entity.VodStreamEntity
import com.grid.tv.data.db.entity.SeriesCategoryEntity
import com.grid.tv.data.db.entity.SeriesShowEntity
import com.grid.tv.data.db.entity.VodUserNotificationEntity
import com.grid.tv.data.db.entity.StreamFailoverStatsEntity
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.data.db.entity.ProfileTasteGenomeEntity
import com.grid.tv.data.db.entity.SubtitleCacheEntity
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
import com.grid.tv.data.db.entity.MovieDetailsEntity
import com.grid.tv.data.db.entity.UserProfileEntity
import com.grid.tv.data.db.entity.WatchHistoryEntity

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        ProgramEntity::class,
        WatchHistoryEntity::class,
        RecordingEntity::class,
        UserProfileEntity::class,
        ActiveProfileEntity::class,
        ProfileFavoriteEntity::class,
        ProfileWatchHistoryEntity::class,
        ProfileSettingsEntity::class,
        StreamHealthEntity::class,
        StreamFailoverStatsEntity::class,
        ScheduledRecordingEntity::class,
        RecordedMediaEntity::class,
        SeriesRecordingRuleEntity::class,
        EpgSourceChannelEntity::class,
        EpgResolutionSuggestionEntity::class,
        FavoriteGroupEntity::class,
        ChannelScanEntity::class,
        ContinueWatchingEntity::class,
        TitleEnrichmentEntity::class,
        MovieDetailsEntity::class,
        ProfileTasteGenomeEntity::class,
        SubtitleCacheEntity::class,
        CanonicalChannelEntity::class,
        EpgLearnedMappingEntity::class,
        EpgMatchAnalyticsEntity::class,
        EpgAliasHitEntity::class,
        PlaybackSessionTelemetryEntity::class,
        StreamSourceHealthEntity::class,
        ChannelHealthAggregateEntity::class,
        ProviderHealthAggregateEntity::class,
        VodWatchEventEntity::class,
        SeriesFollowEntity::class,
        VodCatalogEpisodeEntity::class,
        VodUserNotificationEntity::class,
        VodStreamEntity::class,
        VodCategoryEntity::class,
        SeriesShowEntity::class,
        SeriesCategoryEntity::class,
        ProfileGenreAffinityEntity::class,
        FeaturedBannerStatsEntity::class
    ],
    version = 31,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun programDao(): ProgramDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun recordingDao(): RecordingDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileFavoriteDao(): ProfileFavoriteDao
    abstract fun profileWatchHistoryDao(): ProfileWatchHistoryDao
    abstract fun profileSettingsDao(): ProfileSettingsDao
    abstract fun profileTasteGenomeDao(): ProfileTasteGenomeDao
    abstract fun titleEnrichmentDao(): TitleEnrichmentDao
    abstract fun movieDetailsDao(): MovieDetailsDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao
    abstract fun streamHealthDao(): StreamHealthDao
    abstract fun streamFailoverStatsDao(): StreamFailoverStatsDao
    abstract fun scheduledRecordingDao(): ScheduledRecordingDao
    abstract fun recordedMediaDao(): RecordedMediaDao
    abstract fun epgSourceChannelDao(): EpgSourceChannelDao
    abstract fun epgResolutionSuggestionDao(): EpgResolutionSuggestionDao
    abstract fun favoriteGroupDao(): FavoriteGroupDao
    abstract fun channelScanDao(): ChannelScanDao
    abstract fun seriesRecordingRuleDao(): SeriesRecordingRuleDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun canonicalChannelDao(): CanonicalChannelDao
    abstract fun epgLearnedMappingDao(): EpgLearnedMappingDao
    abstract fun epgMatchAnalyticsDao(): EpgMatchAnalyticsDao
    abstract fun epgAliasHitDao(): EpgAliasHitDao
    abstract fun playbackSessionTelemetryDao(): PlaybackSessionTelemetryDao
    abstract fun streamSourceHealthDao(): StreamSourceHealthDao
    abstract fun channelHealthAggregateDao(): ChannelHealthAggregateDao
    abstract fun providerHealthAggregateDao(): ProviderHealthAggregateDao
    abstract fun vodWatchEventDao(): VodWatchEventDao
    abstract fun seriesFollowDao(): SeriesFollowDao
    abstract fun vodCatalogEpisodeDao(): VodCatalogEpisodeDao
    abstract fun vodUserNotificationDao(): VodUserNotificationDao
    abstract fun vodStreamDao(): VodStreamDao
    abstract fun vodCategoryDao(): VodCategoryDao
    abstract fun seriesShowDao(): SeriesShowDao
    abstract fun seriesCategoryDao(): SeriesCategoryDao
    abstract fun featuredCurationDao(): FeaturedCurationDao

    /** Single transaction for VOD playlist refresh — avoids per-batch WAL fsync churn. */
    @Transaction
    open suspend fun replaceVodStreamsForPlaylist(playlistId: Long, onInsert: suspend () -> Unit) {
        vodStreamDao().clearByPlaylist(playlistId)
        onInsert()
    }

    /** Single transaction for VOD category refresh. */
    @Transaction
    open suspend fun replaceVodCategoriesForPlaylist(
        playlistId: Long,
        categories: List<com.grid.tv.data.db.entity.VodCategoryEntity>
    ) {
        vodCategoryDao().clearByPlaylist(playlistId)
        if (categories.isNotEmpty()) {
            vodCategoryDao().insertAll(categories)
        }
    }

    /** Single transaction for series playlist refresh. */
    @Transaction
    open suspend fun replaceSeriesShowsForPlaylist(playlistId: Long, onInsert: suspend () -> Unit) {
        seriesShowDao().clearByPlaylist(playlistId)
        onInsert()
    }

    /** Single transaction for series category refresh. */
    @Transaction
    open suspend fun replaceSeriesCategoriesForPlaylist(
        playlistId: Long,
        categories: List<com.grid.tv.data.db.entity.SeriesCategoryEntity>
    ) {
        seriesCategoryDao().clearByPlaylist(playlistId)
        if (categories.isNotEmpty()) {
            seriesCategoryDao().insertAll(categories)
        }
    }

    /** Single transaction for EPG channel + programme bulk import per playlist. */
    @Transaction
    open suspend fun importEpgForPlaylist(
        playlistId: Long,
        sourceKey: String,
        sourceChannels: List<EpgSourceChannelEntity>,
        programs: List<ProgramEntity>,
        playlist: PlaylistEntity,
        refreshedAt: Long
    ): Int {
        var resetCount = 0
        if (sourceChannels.isNotEmpty()) {
            epgSourceChannelDao().clearBySource(sourceKey)
            epgSourceChannelDao().insertAll(sourceChannels)
        }
        if (programs.isNotEmpty()) {
            programs.chunked(EPG_PROGRAM_INSERT_CHUNK).forEach { batch ->
                programDao().insertAll(batch)
            }
            playlistDao().update(playlist.copy(lastRefreshed = refreshedAt))
        }
        return resetCount
    }

    private companion object {
        const val EPG_PROGRAM_INSERT_CHUNK = 5_000
    }
}
