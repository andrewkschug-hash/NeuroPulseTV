package com.neuropulse.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.ChannelScanDao
import com.neuropulse.tv.data.db.dao.EpgResolutionSuggestionDao
import com.neuropulse.tv.data.db.dao.FavoriteDao
import com.neuropulse.tv.data.db.dao.FavoriteGroupDao
import com.neuropulse.tv.data.db.dao.PlaylistDao
import com.neuropulse.tv.data.db.dao.ProfileDao
import com.neuropulse.tv.data.db.dao.ProfileFavoriteDao
import com.neuropulse.tv.data.db.dao.ProfileSettingsDao
import com.neuropulse.tv.data.db.dao.ProfileWatchHistoryDao
import com.neuropulse.tv.data.db.dao.ProgramDao
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.EpgSourceChannelDao
import com.neuropulse.tv.data.db.dao.RecordingDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.dao.StreamHealthDao
import com.neuropulse.tv.data.db.dao.WatchHistoryDao
import com.neuropulse.tv.data.db.entity.ActiveProfileEntity
import com.neuropulse.tv.data.db.entity.ChannelEntity
import com.neuropulse.tv.data.db.entity.ChannelScanEntity
import com.neuropulse.tv.data.db.entity.FavoriteEntity
import com.neuropulse.tv.data.db.entity.FavoriteGroupEntity
import com.neuropulse.tv.data.db.entity.PlaylistEntity
import com.neuropulse.tv.data.db.entity.ProfileFavoriteEntity
import com.neuropulse.tv.data.db.entity.ProfileSettingsEntity
import com.neuropulse.tv.data.db.entity.ProfileWatchHistoryEntity
import com.neuropulse.tv.data.db.entity.ProgramEntity
import com.neuropulse.tv.data.db.entity.RecordedMediaEntity
import com.neuropulse.tv.data.db.entity.EpgSourceChannelEntity
import com.neuropulse.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.neuropulse.tv.data.db.entity.RecordingEntity
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.data.db.entity.StreamHealthEntity
import com.neuropulse.tv.data.db.entity.UserProfileEntity
import com.neuropulse.tv.data.db.entity.WatchHistoryEntity

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
        ScheduledRecordingEntity::class,
        RecordedMediaEntity::class,
        EpgSourceChannelEntity::class,
        EpgResolutionSuggestionEntity::class,
        FavoriteGroupEntity::class,
        ChannelScanEntity::class
    ],
    version = 12,
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
    abstract fun streamHealthDao(): StreamHealthDao
    abstract fun scheduledRecordingDao(): ScheduledRecordingDao
    abstract fun recordedMediaDao(): RecordedMediaDao
    abstract fun epgSourceChannelDao(): EpgSourceChannelDao
    abstract fun epgResolutionSuggestionDao(): EpgResolutionSuggestionDao
    abstract fun favoriteGroupDao(): FavoriteGroupDao
    abstract fun channelScanDao(): ChannelScanDao
}
