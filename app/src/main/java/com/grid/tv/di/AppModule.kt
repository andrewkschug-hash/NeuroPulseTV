package com.grid.tv.di

import android.content.Context
import android.app.AlarmManager
import androidx.room.Room
import androidx.work.WorkManager
import com.grid.tv.data.db.AppDatabase
import com.grid.tv.data.db.DbMigrations
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
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.PlaylistDao
import com.grid.tv.data.db.dao.ProfileDao
import com.grid.tv.data.db.dao.ProfileFavoriteDao
import com.grid.tv.data.db.dao.ProfileSettingsDao
import com.grid.tv.data.db.dao.ProfileTasteGenomeDao
import com.grid.tv.data.db.dao.ProfileWatchHistoryDao
import com.grid.tv.data.db.dao.ProgramDao
import com.grid.tv.data.db.dao.RecordedMediaDao
import com.grid.tv.data.db.dao.RecordingDao
import com.grid.tv.data.db.dao.ScheduledRecordingDao
import com.grid.tv.data.db.dao.SeriesRecordingRuleDao
import com.grid.tv.data.db.dao.StreamFailoverStatsDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.dao.WatchHistoryDao
import com.grid.tv.data.db.dao.SubtitleCacheDao
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.data.sync.CloudSyncClient
import com.grid.tv.data.sync.LocalOnlyCloudSyncClient
import com.grid.tv.data.network.parser.M3uParser
import com.grid.tv.data.network.parser.XtreamParser
import com.grid.tv.data.network.parser.XmlTvParser
import com.grid.tv.data.repository.IptvRepositoryImpl
import com.grid.tv.domain.repository.IptvRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "streamflow.db")
            .addMigrations(DbMigrations.MIGRATION_2_3)
            .addMigrations(DbMigrations.MIGRATION_3_4)
            .addMigrations(DbMigrations.MIGRATION_4_5)
            .addMigrations(DbMigrations.MIGRATION_5_6)
            .addMigrations(DbMigrations.MIGRATION_6_7)
            .addMigrations(DbMigrations.MIGRATION_7_8)
            .addMigrations(DbMigrations.MIGRATION_8_9)
            .addMigrations(DbMigrations.MIGRATION_9_10)
            .addMigrations(DbMigrations.MIGRATION_10_11)
            .addMigrations(DbMigrations.MIGRATION_11_12)
            .addMigrations(DbMigrations.MIGRATION_12_13)
            .addMigrations(DbMigrations.MIGRATION_13_14)
            .addMigrations(DbMigrations.MIGRATION_14_15)
            .addMigrations(DbMigrations.MIGRATION_15_16)
            .addMigrations(DbMigrations.MIGRATION_16_17)
            .addMigrations(DbMigrations.MIGRATION_17_18)
            .addMigrations(DbMigrations.MIGRATION_18_19)
            .addMigrations(DbMigrations.MIGRATION_19_20)
            .addMigrations(DbMigrations.MIGRATION_20_21)
            .addMigrations(DbMigrations.MIGRATION_21_22)
            .addMigrations(DbMigrations.MIGRATION_22_23)
            .addMigrations(DbMigrations.MIGRATION_23_24)
             .build()

    @Provides
    fun provideM3uParser(): M3uParser = M3uParser()

    @Provides
    fun provideXmlTvParser(): XmlTvParser = XmlTvParser()

    @Provides
    fun provideXtreamParser(): XtreamParser = XtreamParser()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideChannelScanDao(db: AppDatabase): ChannelScanDao = db.channelScanDao()

    @Provides
    fun provideSeriesRecordingRuleDao(db: AppDatabase): SeriesRecordingRuleDao = db.seriesRecordingRuleDao()

    @Provides
    fun provideProgramDao(db: AppDatabase): ProgramDao = db.programDao()

    @Provides
    fun provideRecordingDao(db: AppDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchHistoryDao(db: AppDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideProfileFavoriteDao(db: AppDatabase): ProfileFavoriteDao = db.profileFavoriteDao()

    @Provides
    fun provideProfileWatchHistoryDao(db: AppDatabase): ProfileWatchHistoryDao = db.profileWatchHistoryDao()

    @Provides
    fun provideProfileSettingsDao(db: AppDatabase): ProfileSettingsDao = db.profileSettingsDao()

    @Provides
    fun provideStreamHealthDao(db: AppDatabase): StreamHealthDao = db.streamHealthDao()

    @Provides
    @Singleton
    fun provideStreamFailoverStatsDao(db: AppDatabase): StreamFailoverStatsDao = db.streamFailoverStatsDao()

    @Provides
    fun provideTitleEnrichmentDao(db: AppDatabase): TitleEnrichmentDao = db.titleEnrichmentDao()

    @Provides
    fun provideSubtitleCacheDao(db: AppDatabase): SubtitleCacheDao = db.subtitleCacheDao()

    @Provides
    fun provideProfileTasteGenomeDao(db: AppDatabase): ProfileTasteGenomeDao = db.profileTasteGenomeDao()

    @Provides
    fun provideScheduledRecordingDao(db: AppDatabase): ScheduledRecordingDao = db.scheduledRecordingDao()

    @Provides
    fun provideRecordedMediaDao(db: AppDatabase): RecordedMediaDao = db.recordedMediaDao()

    @Provides
    fun provideEpgSourceChannelDao(db: AppDatabase): EpgSourceChannelDao = db.epgSourceChannelDao()

    @Provides
    fun provideEpgResolutionSuggestionDao(db: AppDatabase): EpgResolutionSuggestionDao = db.epgResolutionSuggestionDao()

    @Provides
    fun provideFavoriteGroupDao(db: AppDatabase): FavoriteGroupDao = db.favoriteGroupDao()

    @Provides
    fun provideContinueWatchingDao(db: AppDatabase): ContinueWatchingDao = db.continueWatchingDao()

    @Provides
    fun provideCanonicalChannelDao(db: AppDatabase): CanonicalChannelDao = db.canonicalChannelDao()

    @Provides
    fun provideEpgLearnedMappingDao(db: AppDatabase): EpgLearnedMappingDao = db.epgLearnedMappingDao()

    @Provides
    fun provideEpgMatchAnalyticsDao(db: AppDatabase): EpgMatchAnalyticsDao = db.epgMatchAnalyticsDao()

    @Provides
    fun provideEpgAliasHitDao(db: AppDatabase): EpgAliasHitDao = db.epgAliasHitDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    abstract fun bindRepository(impl: IptvRepositoryImpl): IptvRepository

    @Binds
    abstract fun bindCloudSync(impl: LocalOnlyCloudSyncClient): CloudSyncClient
}
