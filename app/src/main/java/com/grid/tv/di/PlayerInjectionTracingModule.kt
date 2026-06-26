package com.grid.tv.di

import com.grid.tv.data.catalog.CatalogHydrationGuard
import com.grid.tv.feature.health.intelligence.PlaybackTelemetryCollector
import com.grid.tv.feature.startup.StartupDependencyProbe
import com.grid.tv.feature.startup.StartupSafety
import com.grid.tv.player.DecoderPressureTracker
import com.grid.tv.player.IptvStreamFormatProber
import com.grid.tv.player.IptvStreamFormatRegistry
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.PlaybackHealthMonitor
import com.grid.tv.player.PlaybackHttpDataSourceFactory
import com.grid.tv.player.PlaybackMetricsLogger
import com.grid.tv.player.PlaybackNetworkCoordinator
import com.grid.tv.player.PlaybackNetworkExclusivity
import com.grid.tv.player.PlayerFactory
import com.grid.tv.player.StreamFailoverController
import com.grid.tv.player.StreamTypePreflightSniffer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerInjectionTracingModule {

    @Provides
    @Singleton
    fun provideLivePlayerManager(
        playerFactoryProvider: Provider<PlayerFactory>,
        playbackHttpDataSourceFactoryProvider: Provider<PlaybackHttpDataSourceFactory>,
        streamFailoverProvider: Provider<StreamFailoverController>,
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
        StartupDependencyProbe.traceCreate("LivePlayerManager") {
            LivePlayerManager(
                playerFactoryProvider.get(),
                playbackHttpDataSourceFactoryProvider.get(),
                streamFailoverProvider.get(),
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
}
