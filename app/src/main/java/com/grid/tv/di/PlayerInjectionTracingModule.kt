package com.grid.tv.di

import com.grid.tv.data.catalog.CatalogHydrationGuard
import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryCollector
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryReporter
import com.grid.tv.feature.health.intelligence.StreamHealthAggregator
import com.grid.tv.feature.preview.PreviewPlayerManager
import com.grid.tv.feature.startup.StartupDependencyProbe
import com.grid.tv.feature.startup.StartupSafety
import com.grid.tv.player.DecoderPressureTracker
import com.grid.tv.player.IptvStreamFormatProber
import com.grid.tv.player.IptvStreamFormatRegistry
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.MultiPanePlaybackPool
import com.grid.tv.player.MultiPanePlaybackWatchdog
import com.grid.tv.player.PictureInPictureController
import com.grid.tv.player.PlaybackHealthMonitor
import com.grid.tv.player.PlaybackHttpDataSourceFactory
import com.grid.tv.player.PlaybackMetricsLogger
import com.grid.tv.player.PlaybackNetworkCoordinator
import com.grid.tv.player.PlaybackNetworkExclusivity
import com.grid.tv.player.PlaybackOrchestrator
import com.grid.tv.player.PlaybackScannerIsolation
import com.grid.tv.player.PlayerFactory
import com.grid.tv.player.StreamFailoverAnalytics
import com.grid.tv.player.StreamFailoverController
import com.grid.tv.player.StreamTypePreflightSniffer
import com.grid.tv.player.VodPlaybackNetworkGuard
import com.grid.tv.player.multiview.MultiViewManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.Lazy
import javax.inject.Provider
import javax.inject.Singleton

private val COROUTINE = setOf("coroutine_launch")
private val MAIN_LOOPER = setOf("looper_access", "main_immediate_scope")
private val SYNC_BLOCK = setOf("synchronized")
private val ROOM_HINT = setOf("room_dao_injection")
private val LAZY_OKHTTP = setOf("lazy_okhttp_delegate")

@Module
@InstallIn(SingletonComponent::class)
object PlayerInjectionTracingModule {

    @Provides
    @Singleton
    fun provideDecoderPressureTracker(): DecoderPressureTracker =
        StartupDependencyProbe.traceCreate("DecoderPressureTracker") {
            DecoderPressureTracker()
        }

    @Provides
    @Singleton
    fun provideCatalogHydrationGuard(): CatalogHydrationGuard =
        StartupDependencyProbe.traceCreate("CatalogHydrationGuard") {
            CatalogHydrationGuard()
        }

    @Provides
    @Singleton
    fun provideIptvStreamFormatRegistry(): IptvStreamFormatRegistry =
        StartupDependencyProbe.traceCreate("IptvStreamFormatRegistry") {
            IptvStreamFormatRegistry()
        }

    @Provides
    @Singleton
    fun providePlaybackScannerIsolation(
        channelScannerProvider: Provider<com.grid.tv.feature.scanner.ChannelScanner>
    ): PlaybackScannerIsolation =
        StartupDependencyProbe.traceCreate("PlaybackScannerIsolation", blockingHints = setOf("handler_executor")) {
            PlaybackScannerIsolation(channelScannerProvider)
        }

    @Provides
    @Singleton
    fun provideVodPlaybackNetworkGuard(
        liveNetworkExclusivityProvider: Provider<PlaybackNetworkExclusivity>,
        playbackScannerIsolationProvider: Provider<PlaybackScannerIsolation>
    ): VodPlaybackNetworkGuard =
        StartupDependencyProbe.traceCreate("VodPlaybackNetworkGuard", blockingHints = SYNC_BLOCK) {
            VodPlaybackNetworkGuard(
                liveNetworkExclusivityProvider.get(),
                playbackScannerIsolationProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePlaybackNetworkExclusivity(
        playbackScannerIsolationProvider: Provider<PlaybackScannerIsolation>
    ): PlaybackNetworkExclusivity =
        StartupDependencyProbe.traceCreate("PlaybackNetworkExclusivity", blockingHints = SYNC_BLOCK) {
            PlaybackNetworkExclusivity(playbackScannerIsolationProvider.get())
        }

    @Provides
    @Singleton
    fun providePlaybackNetworkCoordinator(
        appHttpClientProvider: Provider<AppHttpClient>,
        playbackNetworkExclusivityProvider: Provider<PlaybackNetworkExclusivity>,
        vodPlaybackNetworkGuardProvider: Provider<VodPlaybackNetworkGuard>,
        streamFormatRegistryProvider: Provider<IptvStreamFormatRegistry>
    ): PlaybackNetworkCoordinator =
        StartupDependencyProbe.traceCreate("PlaybackNetworkCoordinator", blockingHints = SYNC_BLOCK) {
            PlaybackNetworkCoordinator(
                appHttpClientProvider.get(),
                playbackNetworkExclusivityProvider.get(),
                vodPlaybackNetworkGuardProvider.get(),
                streamFormatRegistryProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePlaybackHttpDataSourceFactory(
        appHttpClientProvider: Provider<AppHttpClient>,
        playbackMetricsLoggerProvider: Provider<PlaybackMetricsLogger>,
        streamFormatRegistryProvider: Provider<IptvStreamFormatRegistry>
    ): PlaybackHttpDataSourceFactory =
        StartupDependencyProbe.traceCreate("PlaybackHttpDataSourceFactory", blockingHints = SYNC_BLOCK + LAZY_OKHTTP) {
            PlaybackHttpDataSourceFactory(
                appHttpClientProvider.get(),
                playbackMetricsLoggerProvider.get(),
                streamFormatRegistryProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePlayerFactory(
        playbackHttpDataSourceFactoryProvider: Provider<PlaybackHttpDataSourceFactory>,
        decoderPressureTrackerProvider: Provider<DecoderPressureTracker>
    ): PlayerFactory =
        StartupDependencyProbe.traceCreate("PlayerFactory") {
            PlayerFactory(
                playbackHttpDataSourceFactoryProvider.get(),
                decoderPressureTrackerProvider.get()
            )
        }

    @Provides
    @Singleton
    fun provideStreamFailoverAnalytics(
        streamFailoverStatsDaoProvider: Provider<com.grid.tv.data.db.dao.StreamFailoverStatsDao>
    ): StreamFailoverAnalytics =
        StartupDependencyProbe.traceCreate("StreamFailoverAnalytics", blockingHints = COROUTINE + ROOM_HINT) {
            StreamFailoverAnalytics(streamFailoverStatsDaoProvider.get())
        }

    @Provides
    @Singleton
    fun providePlaybackTelemetryReporter(
        scoringEngineProvider: Provider<com.grid.tv.feature.health.intelligence.StreamHealthScoringEngine>
    ): PlaybackTelemetryReporter =
        StartupDependencyProbe.traceCreate("PlaybackTelemetryReporter") {
            PlaybackTelemetryReporter(scoringEngineProvider.get())
        }

    @Provides
    @Singleton
    fun provideStreamHealthAggregator(
        telemetryDaoProvider: Provider<com.grid.tv.data.db.dao.PlaybackSessionTelemetryDao>,
        streamSourceHealthDaoProvider: Provider<com.grid.tv.data.db.dao.StreamSourceHealthDao>,
        channelHealthDaoProvider: Provider<com.grid.tv.data.db.dao.ChannelHealthAggregateDao>,
        providerHealthDaoProvider: Provider<com.grid.tv.data.db.dao.ProviderHealthAggregateDao>,
        legacyStreamHealthDaoProvider: Provider<com.grid.tv.data.db.dao.StreamHealthDao>,
        channelDaoProvider: Provider<com.grid.tv.data.db.dao.ChannelDao>,
        scoringEngineProvider: Provider<com.grid.tv.feature.health.intelligence.StreamHealthScoringEngine>
    ): StreamHealthAggregator =
        StartupDependencyProbe.traceCreate("StreamHealthAggregator", blockingHints = ROOM_HINT) {
            StreamHealthAggregator(
                telemetryDaoProvider.get(),
                streamSourceHealthDaoProvider.get(),
                channelHealthDaoProvider.get(),
                providerHealthDaoProvider.get(),
                legacyStreamHealthDaoProvider.get(),
                channelDaoProvider.get(),
                scoringEngineProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePlaybackTelemetryCollector(
        aggregatorProvider: Provider<StreamHealthAggregator>,
        reporterProvider: Provider<PlaybackTelemetryReporter>
    ): PlaybackTelemetryCollector =
        StartupDependencyProbe.traceCreate("PlaybackTelemetryCollector", blockingHints = setOf("synchronized_mutex")) {
            PlaybackTelemetryCollector(aggregatorProvider.get(), reporterProvider.get())
        }

    @Provides
    @Singleton
    fun providePlaybackMetricsLogger(
        telemetryCollectorProvider: Provider<PlaybackTelemetryCollector>
    ): PlaybackMetricsLogger =
        StartupDependencyProbe.traceCreate("PlaybackMetricsLogger") {
            PlaybackMetricsLogger(telemetryCollectorProvider.get())
        }

    @Provides
    @Singleton
    fun provideStreamFailoverController(
        analyticsProvider: Provider<StreamFailoverAnalytics>,
        playbackMetricsProvider: Provider<PlaybackMetricsLogger>,
        playbackTelemetryProvider: Provider<PlaybackTelemetryCollector>,
        healthAggregatorProvider: Provider<StreamHealthAggregator>,
        playbackNetworkCoordinatorProvider: Provider<PlaybackNetworkCoordinator>
    ): StreamFailoverController =
        StartupDependencyProbe.traceCreate("StreamFailoverController", blockingHints = COROUTINE + MAIN_LOOPER) {
            StreamFailoverController(
                analyticsProvider.get(),
                playbackMetricsProvider.get(),
                playbackTelemetryProvider.get(),
                healthAggregatorProvider.get(),
                playbackNetworkCoordinatorProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePlaybackHealthMonitor(
        metricsProvider: Provider<PlaybackMetricsLogger>,
        telemetryProvider: Provider<PlaybackTelemetryCollector>
    ): PlaybackHealthMonitor =
        StartupDependencyProbe.traceCreate("PlaybackHealthMonitor") {
            PlaybackHealthMonitor(metricsProvider.get(), telemetryProvider.get())
        }

    @Provides
    @Singleton
    fun provideIptvStreamFormatProber(
        appHttpClientProvider: Provider<AppHttpClient>,
        registryProvider: Provider<IptvStreamFormatRegistry>,
        playbackNetworkCoordinatorProvider: Provider<PlaybackNetworkCoordinator>
    ): IptvStreamFormatProber =
        StartupDependencyProbe.traceCreate("IptvStreamFormatProber", blockingHints = LAZY_OKHTTP) {
            IptvStreamFormatProber(
                appHttpClientProvider.get(),
                registryProvider.get(),
                playbackNetworkCoordinatorProvider.get()
            )
        }

    @Provides
    @Singleton
    fun provideStreamTypePreflightSniffer(
        appHttpClientProvider: Provider<AppHttpClient>
    ): StreamTypePreflightSniffer =
        StartupDependencyProbe.traceCreate("StreamTypePreflightSniffer", blockingHints = LAZY_OKHTTP) {
            StreamTypePreflightSniffer(appHttpClientProvider.get())
        }

    @Provides
    @Singleton
    fun provideMultiPanePlaybackWatchdog(
        streamFormatRegistryProvider: Provider<IptvStreamFormatRegistry>,
        streamFormatProberProvider: Provider<IptvStreamFormatProber>,
        playbackNetworkCoordinatorProvider: Provider<PlaybackNetworkCoordinator>
    ): MultiPanePlaybackWatchdog =
        StartupDependencyProbe.traceCreate("MultiPanePlaybackWatchdog", blockingHints = COROUTINE + MAIN_LOOPER) {
            MultiPanePlaybackWatchdog(
                streamFormatRegistryProvider.get(),
                streamFormatProberProvider.get(),
                playbackNetworkCoordinatorProvider.get()
            )
        }

    @Provides
    @Singleton
    fun provideMultiPanePlaybackPool(
        playerFactoryProvider: Provider<PlayerFactory>,
        streamFormatRegistryProvider: Provider<IptvStreamFormatRegistry>,
        streamFormatProberProvider: Provider<IptvStreamFormatProber>,
        streamTypePreflightSnifferProvider: Provider<StreamTypePreflightSniffer>,
        paneWatchdogProvider: Provider<MultiPanePlaybackWatchdog>,
        decoderPressureTrackerProvider: Provider<DecoderPressureTracker>,
        playbackNetworkExclusivityProvider: Provider<PlaybackNetworkExclusivity>,
        playbackNetworkCoordinatorProvider: Provider<PlaybackNetworkCoordinator>
    ): MultiPanePlaybackPool =
        StartupDependencyProbe.traceCreate("MultiPanePlaybackPool", blockingHints = COROUTINE + MAIN_LOOPER) {
            MultiPanePlaybackPool(
                playerFactoryProvider.get(),
                streamFormatRegistryProvider.get(),
                streamFormatProberProvider.get(),
                streamTypePreflightSnifferProvider.get(),
                paneWatchdogProvider.get(),
                decoderPressureTrackerProvider.get(),
                playbackNetworkExclusivityProvider.get(),
                playbackNetworkCoordinatorProvider.get()
            )
        }

    @Provides
    @Singleton
    fun provideLivePlayerManager(
        playerFactoryProvider: Provider<PlayerFactory>,
        playbackHttpDataSourceFactoryProvider: Provider<PlaybackHttpDataSourceFactory>,
        streamFailoverLazy: Lazy<StreamFailoverController>,
        catalogHydrationGuardProvider: Provider<CatalogHydrationGuard>,
        decoderPressureTrackerProvider: Provider<DecoderPressureTracker>,
        playbackMetricsProvider: Provider<PlaybackMetricsLogger>,
        playbackTelemetryProvider: Provider<PlaybackTelemetryCollector>,
        streamFormatRegistryProvider: Provider<IptvStreamFormatRegistry>,
        playbackHealthMonitorProvider: Provider<PlaybackHealthMonitor>,
        streamFormatProberProvider: Provider<IptvStreamFormatProber>,
        playbackNetworkExclusivityProvider: Provider<PlaybackNetworkExclusivity>,
        playbackNetworkCoordinatorProvider: Provider<PlaybackNetworkCoordinator>,
        streamTypePreflightSnifferProvider: Provider<StreamTypePreflightSniffer>,
        startupSafetyProvider: Provider<StartupSafety>
    ): LivePlayerManager =
        StartupDependencyProbe.traceCreate("LivePlayerManager", blockingHints = COROUTINE + MAIN_LOOPER) {
            LivePlayerManager(
                playerFactoryProvider.get(),
                playbackHttpDataSourceFactoryProvider.get(),
                streamFailoverLazy,
                catalogHydrationGuardProvider.get(),
                decoderPressureTrackerProvider.get(),
                playbackMetricsProvider.get(),
                playbackTelemetryProvider.get(),
                streamFormatRegistryProvider.get(),
                playbackHealthMonitorProvider.get(),
                streamFormatProberProvider.get(),
                playbackNetworkExclusivityProvider.get(),
                playbackNetworkCoordinatorProvider.get(),
                streamTypePreflightSnifferProvider.get(),
                startupSafetyProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePlaybackOrchestrator(
        livePlayerManagerProvider: Provider<LivePlayerManager>,
        decoderPressureTrackerProvider: Provider<DecoderPressureTracker>,
        multiPanePlaybackPoolProvider: Provider<MultiPanePlaybackPool>
    ): PlaybackOrchestrator =
        StartupDependencyProbe.traceCreate("PlaybackOrchestrator") {
            PlaybackOrchestrator(
                livePlayerManagerProvider.get(),
                decoderPressureTrackerProvider.get(),
                multiPanePlaybackPoolProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePreviewPlayerManager(
        livePlayerManagerProvider: Provider<LivePlayerManager>,
        playbackOrchestratorProvider: Provider<PlaybackOrchestrator>
    ): PreviewPlayerManager =
        StartupDependencyProbe.traceCreate("PreviewPlayerManager") {
            PreviewPlayerManager(
                livePlayerManagerProvider.get(),
                playbackOrchestratorProvider.get()
            )
        }

    @Provides
    @Singleton
    fun providePictureInPictureController(): PictureInPictureController =
        StartupDependencyProbe.traceCreate("PictureInPictureController") {
            PictureInPictureController()
        }

    @Provides
    @Singleton
    fun provideMultiViewManager(
        multiPanePlaybackPoolProvider: Provider<MultiPanePlaybackPool>
    ): MultiViewManager =
        StartupDependencyProbe.traceCreate("MultiViewManager") {
            MultiViewManager(multiPanePlaybackPoolProvider.get())
        }
}
