package com.grid.tv.di

import android.content.Context
import android.app.AlarmManager
import androidx.work.WorkManager
import com.grid.tv.data.db.AppDatabase
import com.grid.tv.data.db.AppDatabaseHolder
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
import com.grid.tv.data.db.dao.PlaybackSessionTelemetryDao
import com.grid.tv.data.db.dao.StreamSourceHealthDao
import com.grid.tv.data.db.dao.ChannelHealthAggregateDao
import com.grid.tv.data.db.dao.ProviderHealthAggregateDao
import com.grid.tv.data.db.dao.VodWatchEventDao
import com.grid.tv.data.db.dao.SeriesFollowDao
import com.grid.tv.data.db.dao.VodCatalogEpisodeDao
import com.grid.tv.data.db.dao.VodUserNotificationDao
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
        AppDatabaseHolder.get(context)

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

    @Provides
    fun providePlaybackSessionTelemetryDao(db: AppDatabase): PlaybackSessionTelemetryDao =
        db.playbackSessionTelemetryDao()

    @Provides
    fun provideStreamSourceHealthDao(db: AppDatabase): StreamSourceHealthDao = db.streamSourceHealthDao()

    @Provides
    fun provideChannelHealthAggregateDao(db: AppDatabase): ChannelHealthAggregateDao =
        db.channelHealthAggregateDao()

    @Provides
    fun provideProviderHealthAggregateDao(db: AppDatabase): ProviderHealthAggregateDao =
        db.providerHealthAggregateDao()

    @Provides
    fun provideVodWatchEventDao(db: AppDatabase): VodWatchEventDao = db.vodWatchEventDao()

    @Provides
    fun provideSeriesFollowDao(db: AppDatabase): SeriesFollowDao = db.seriesFollowDao()

    @Provides
    fun provideVodCatalogEpisodeDao(db: AppDatabase): VodCatalogEpisodeDao = db.vodCatalogEpisodeDao()

    @Provides
    fun provideVodUserNotificationDao(db: AppDatabase): VodUserNotificationDao = db.vodUserNotificationDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    abstract fun bindRepository(impl: IptvRepositoryImpl): IptvRepository

    @Binds
    abstract fun bindCloudSync(impl: LocalOnlyCloudSyncClient): CloudSyncClient
}
