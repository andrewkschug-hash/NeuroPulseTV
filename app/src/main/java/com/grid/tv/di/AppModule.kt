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
import com.grid.tv.data.db.dao.PlaylistFavoriteGroupDao
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
import com.grid.tv.data.db.dao.VodCategoryDao
import com.grid.tv.data.db.dao.VodCatalogEpisodeDao
import com.grid.tv.data.db.dao.FeaturedCurationDao
import com.grid.tv.data.db.dao.VodStreamDao
import com.grid.tv.data.db.dao.SeriesCategoryDao
import com.grid.tv.data.db.dao.SeriesShowDao
import com.grid.tv.data.db.dao.VodUserNotificationDao
import com.grid.tv.data.db.dao.StreamFailoverStatsDao
import com.grid.tv.data.db.dao.StreamHealthDao
import com.grid.tv.data.db.dao.WatchHistoryDao
import com.grid.tv.data.db.dao.SubtitleCacheDao
import com.grid.tv.data.db.dao.TitleEnrichmentDao
import com.grid.tv.data.db.dao.MovieDetailsDao
import com.grid.tv.data.sync.CloudSyncClient
import com.grid.tv.data.sync.LocalOnlyCloudSyncClient
import com.grid.tv.feature.network.introdb.IntroDbClient
import com.grid.tv.feature.scanner.ChannelScanGate
import com.grid.tv.feature.scanner.ChannelScanner
import com.grid.tv.feature.scanner.HostFailureTracker
import com.grid.tv.feature.startup.StartupDependencyProbe
import com.grid.tv.util.cache.AppCacheRegistry
import com.grid.tv.data.network.parser.M3uParser
import com.grid.tv.data.network.parser.XtreamParser
import com.grid.tv.data.network.parser.XmlTvParser
import com.grid.tv.data.repository.IptvRepositoryImpl
import com.grid.tv.data.repository.MovieRepositoryImpl
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.domain.repository.MovieRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        StartupDependencyProbe.traceCreate("AppDatabase") {
            com.grid.tv.feature.startup.StartupTiming.log("AppProvidesModule.provideDb — Creating AppDatabase...")
            com.grid.tv.feature.startup.StartupTiming.trace("AppProvidesModule.provideDb") {
                AppDatabaseHolder.get(context)
            }
        }

    @Provides
    fun provideM3uParser(): M3uParser =
        StartupDependencyProbe.traceCreate("M3uParser") { M3uParser() }

    @Provides
    fun provideXmlTvParser(): XmlTvParser =
        StartupDependencyProbe.traceCreate("XmlTvParser") { XmlTvParser() }

    @Provides
    fun provideXtreamParser(): XtreamParser =
        StartupDependencyProbe.traceCreate("XtreamParser") { XtreamParser() }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        StartupDependencyProbe.traceCreate("WorkManager") { WorkManager.getInstance(context) }

    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        StartupDependencyProbe.traceCreate("AlarmManager") {
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao =
        StartupDependencyProbe.traceCreate("PlaylistDao") { db.playlistDao() }

    @Provides
    fun providePlaylistFavoriteGroupDao(db: AppDatabase): PlaylistFavoriteGroupDao =
        StartupDependencyProbe.traceCreate("PlaylistFavoriteGroupDao") { db.playlistFavoriteGroupDao() }

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao =
        StartupDependencyProbe.traceCreate("ChannelDao") { db.channelDao() }

    @Provides
    fun provideChannelScanDao(db: AppDatabase): ChannelScanDao =
        StartupDependencyProbe.traceCreate("ChannelScanDao") { db.channelScanDao() }

    @Provides
    fun provideSeriesRecordingRuleDao(db: AppDatabase): SeriesRecordingRuleDao =
        StartupDependencyProbe.traceCreate("SeriesRecordingRuleDao") { db.seriesRecordingRuleDao() }

    @Provides
    fun provideProgramDao(db: AppDatabase): ProgramDao =
        StartupDependencyProbe.traceCreate("ProgramDao") { db.programDao() }

    @Provides
    fun provideRecordingDao(db: AppDatabase): RecordingDao =
        StartupDependencyProbe.traceCreate("RecordingDao") { db.recordingDao() }

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao =
        StartupDependencyProbe.traceCreate("ProfileDao") { db.profileDao() }

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao =
        StartupDependencyProbe.traceCreate("FavoriteDao") { db.favoriteDao() }

    @Provides
    fun provideWatchHistoryDao(db: AppDatabase): WatchHistoryDao =
        StartupDependencyProbe.traceCreate("WatchHistoryDao") { db.watchHistoryDao() }

    @Provides
    fun provideProfileFavoriteDao(db: AppDatabase): ProfileFavoriteDao =
        StartupDependencyProbe.traceCreate("ProfileFavoriteDao") { db.profileFavoriteDao() }

    @Provides
    fun provideProfileWatchHistoryDao(db: AppDatabase): ProfileWatchHistoryDao =
        StartupDependencyProbe.traceCreate("ProfileWatchHistoryDao") { db.profileWatchHistoryDao() }

    @Provides
    fun provideProfileSettingsDao(db: AppDatabase): ProfileSettingsDao =
        StartupDependencyProbe.traceCreate("ProfileSettingsDao") { db.profileSettingsDao() }

    @Provides
    fun provideStreamHealthDao(db: AppDatabase): StreamHealthDao =
        StartupDependencyProbe.traceCreate("StreamHealthDao") { db.streamHealthDao() }

    @Provides
    @Singleton
    fun provideStreamFailoverStatsDao(db: AppDatabase): StreamFailoverStatsDao =
        StartupDependencyProbe.traceCreate("StreamFailoverStatsDao") { db.streamFailoverStatsDao() }

    @Provides
    fun provideTitleEnrichmentDao(db: AppDatabase): TitleEnrichmentDao =
        StartupDependencyProbe.traceCreate("TitleEnrichmentDao") { db.titleEnrichmentDao() }

    @Provides
    fun provideMovieDetailsDao(db: AppDatabase): MovieDetailsDao =
        StartupDependencyProbe.traceCreate("MovieDetailsDao") { db.movieDetailsDao() }

    @Provides
    fun provideSubtitleCacheDao(db: AppDatabase): SubtitleCacheDao =
        StartupDependencyProbe.traceCreate("SubtitleCacheDao") { db.subtitleCacheDao() }

    @Provides
    fun provideProfileTasteGenomeDao(db: AppDatabase): ProfileTasteGenomeDao =
        StartupDependencyProbe.traceCreate("ProfileTasteGenomeDao") { db.profileTasteGenomeDao() }

    @Provides
    fun provideScheduledRecordingDao(db: AppDatabase): ScheduledRecordingDao =
        StartupDependencyProbe.traceCreate("ScheduledRecordingDao") { db.scheduledRecordingDao() }

    @Provides
    fun provideRecordedMediaDao(db: AppDatabase): RecordedMediaDao =
        StartupDependencyProbe.traceCreate("RecordedMediaDao") { db.recordedMediaDao() }

    @Provides
    fun provideEpgSourceChannelDao(db: AppDatabase): EpgSourceChannelDao =
        StartupDependencyProbe.traceCreate("EpgSourceChannelDao") { db.epgSourceChannelDao() }

    @Provides
    fun provideEpgResolutionSuggestionDao(db: AppDatabase): EpgResolutionSuggestionDao =
        StartupDependencyProbe.traceCreate("EpgResolutionSuggestionDao") { db.epgResolutionSuggestionDao() }

    @Provides
    fun provideFavoriteGroupDao(db: AppDatabase): FavoriteGroupDao =
        StartupDependencyProbe.traceCreate("FavoriteGroupDao") { db.favoriteGroupDao() }

    @Provides
    fun provideContinueWatchingDao(db: AppDatabase): ContinueWatchingDao =
        StartupDependencyProbe.traceCreate("ContinueWatchingDao") { db.continueWatchingDao() }

    @Provides
    fun provideCanonicalChannelDao(db: AppDatabase): CanonicalChannelDao =
        StartupDependencyProbe.traceCreate("CanonicalChannelDao") { db.canonicalChannelDao() }

    @Provides
    fun provideEpgLearnedMappingDao(db: AppDatabase): EpgLearnedMappingDao =
        StartupDependencyProbe.traceCreate("EpgLearnedMappingDao") { db.epgLearnedMappingDao() }

    @Provides
    fun provideEpgMatchAnalyticsDao(db: AppDatabase): EpgMatchAnalyticsDao =
        StartupDependencyProbe.traceCreate("EpgMatchAnalyticsDao") { db.epgMatchAnalyticsDao() }

    @Provides
    fun provideEpgAliasHitDao(db: AppDatabase): EpgAliasHitDao =
        StartupDependencyProbe.traceCreate("EpgAliasHitDao") { db.epgAliasHitDao() }

    @Provides
    fun providePlaybackSessionTelemetryDao(db: AppDatabase): PlaybackSessionTelemetryDao =
        StartupDependencyProbe.traceCreate("PlaybackSessionTelemetryDao") { db.playbackSessionTelemetryDao() }

    @Provides
    fun provideStreamSourceHealthDao(db: AppDatabase): StreamSourceHealthDao =
        StartupDependencyProbe.traceCreate("StreamSourceHealthDao") { db.streamSourceHealthDao() }

    @Provides
    fun provideChannelHealthAggregateDao(db: AppDatabase): ChannelHealthAggregateDao =
        StartupDependencyProbe.traceCreate("ChannelHealthAggregateDao") { db.channelHealthAggregateDao() }

    @Provides
    fun provideProviderHealthAggregateDao(db: AppDatabase): ProviderHealthAggregateDao =
        StartupDependencyProbe.traceCreate("ProviderHealthAggregateDao") { db.providerHealthAggregateDao() }

    @Provides
    fun provideVodWatchEventDao(db: AppDatabase): VodWatchEventDao =
        StartupDependencyProbe.traceCreate("VodWatchEventDao") { db.vodWatchEventDao() }

    @Provides
    fun provideSeriesFollowDao(db: AppDatabase): SeriesFollowDao =
        StartupDependencyProbe.traceCreate("SeriesFollowDao") { db.seriesFollowDao() }

    @Provides
    fun provideVodCatalogEpisodeDao(db: AppDatabase): VodCatalogEpisodeDao =
        StartupDependencyProbe.traceCreate("VodCatalogEpisodeDao") { db.vodCatalogEpisodeDao() }

    @Provides
    fun provideVodUserNotificationDao(db: AppDatabase): VodUserNotificationDao =
        StartupDependencyProbe.traceCreate("VodUserNotificationDao") { db.vodUserNotificationDao() }

    @Provides
    fun provideVodStreamDao(db: AppDatabase): VodStreamDao =
        StartupDependencyProbe.traceCreate("VodStreamDao") { db.vodStreamDao() }

    @Provides
    fun provideVodCategoryDao(db: AppDatabase): VodCategoryDao =
        StartupDependencyProbe.traceCreate("VodCategoryDao") { db.vodCategoryDao() }

    @Provides
    fun provideSeriesShowDao(db: AppDatabase): SeriesShowDao =
        StartupDependencyProbe.traceCreate("SeriesShowDao") { db.seriesShowDao() }

    @Provides
    fun provideSeriesCategoryDao(db: AppDatabase): SeriesCategoryDao =
        StartupDependencyProbe.traceCreate("SeriesCategoryDao") { db.seriesCategoryDao() }

    @Provides
    fun provideFeaturedCurationDao(db: AppDatabase): FeaturedCurationDao =
        StartupDependencyProbe.traceCreate("FeaturedCurationDao") { db.featuredCurationDao() }

    @Provides
    @Singleton
    fun provideHostFailureTracker(registry: AppCacheRegistry): HostFailureTracker =
        StartupDependencyProbe.traceCreate("HostFailureTracker") {
            HostFailureTracker(registry = registry)
        }

    @Provides
    @Singleton
    fun provideIntroDbClient(): IntroDbClient =
        StartupDependencyProbe.traceCreate("IntroDbClient") { IntroDbClient() }

    @Provides
    @Singleton
    fun provideIptvRepository(implProvider: Provider<IptvRepositoryImpl>): IptvRepository =
        StartupDependencyProbe.traceCreate("IptvRepositoryImpl") { implProvider.get() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    @Singleton
    abstract fun bindMovieRepository(impl: MovieRepositoryImpl): MovieRepository

    @Binds
    abstract fun bindCloudSync(impl: LocalOnlyCloudSyncClient): CloudSyncClient

    @Binds
    @Singleton
    abstract fun bindChannelScanGate(scanner: ChannelScanner): ChannelScanGate
}
