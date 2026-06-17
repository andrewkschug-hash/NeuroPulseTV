package com.grid.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
import com.grid.tv.data.db.dao.StreamFailoverStatsDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.dao.SubtitleCacheDao
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.data.db.dao.WatchHistoryDao
import com.grid.tv.data.db.entity.ContinueWatchingEntity
import com.grid.tv.data.db.entity.ActiveProfileEntity
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.ChannelScanEntity
import com.grid.tv.data.db.entity.FavoriteEntity
import com.grid.tv.data.db.entity.FavoriteGroupEntity
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
import com.grid.tv.data.db.entity.StreamFailoverStatsEntity
import com.grid.tv.data.db.entity.StreamHealthEntity
import com.grid.tv.data.db.entity.ProfileTasteGenomeEntity
import com.grid.tv.data.db.entity.SubtitleCacheEntity
import com.grid.tv.data.db.entity.TitleEnrichmentEntity
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
        ProfileTasteGenomeEntity::class,
        SubtitleCacheEntity::class,
        CanonicalChannelEntity::class,
        EpgLearnedMappingEntity::class,
        EpgMatchAnalyticsEntity::class,
        EpgAliasHitEntity::class
    ],
    version = 24,
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
}
