package com.grid.tv.di

import com.grid.tv.data.io.DiskIoSerialExecutor
import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.preview.PreviewPlayerManager
import com.grid.tv.feature.search.MicSearchTrigger
import com.grid.tv.feature.startup.StartupDependencyProbe
import com.grid.tv.feature.startup.StartupSafety
import com.grid.tv.feature.startup.UiIdleMonitor
import com.grid.tv.player.AppPlayerLifecycleCoordinator
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.MultiPanePlaybackPool
import com.grid.tv.player.PictureInPictureController
import com.grid.tv.player.PlaybackOrchestrator
import com.grid.tv.player.multiview.MultiViewManager
import com.grid.tv.ui.theme.ThemeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Wraps MainActivity's direct @Inject singletons so [StartupDependencyProbe] captures
 * inclusive construction time (nested Provider.get() calls stack under each span).
 */
@Module
@InstallIn(SingletonComponent::class)
object ActivityInjectionTracingModule {

    @Provides
    @Singleton
    fun provideMicSearchTrigger(): MicSearchTrigger =
        StartupDependencyProbe.traceCreate("MicSearchTrigger") {
            MicSearchTrigger()
        }

    @Provides
    @Singleton
    fun provideThemeManager(repositoryProvider: Provider<IptvRepository>): ThemeManager =
        StartupDependencyProbe.traceCreate("ThemeManager") {
            ThemeManager(repositoryProvider.get())
        }

    @Provides
    @Singleton
    fun provideDiskIoSerialExecutor(): DiskIoSerialExecutor =
        StartupDependencyProbe.traceCreate("DiskIoSerialExecutor") {
            DiskIoSerialExecutor()
        }

    @Provides
    @Singleton
    fun provideUiIdleMonitor(): UiIdleMonitor =
        StartupDependencyProbe.traceCreate("UiIdleMonitor") {
            UiIdleMonitor()
        }

    @Provides
    @Singleton
    fun provideStartupSafety(
        diskIoSerialExecutorProvider: Provider<DiskIoSerialExecutor>,
        uiIdleMonitorProvider: Provider<UiIdleMonitor>
    ): StartupSafety =
        StartupDependencyProbe.traceCreate("StartupSafety") {
            StartupSafety(
                diskIoSerialExecutorProvider.get(),
                uiIdleMonitorProvider.get()
            )
        }

    @Provides
    @Singleton
    fun provideAppPlayerLifecycleCoordinator(
        livePlayerManagerProvider: Provider<LivePlayerManager>,
        previewPlayerManagerProvider: Provider<PreviewPlayerManager>,
        pipControllerProvider: Provider<PictureInPictureController>,
        playbackOrchestratorProvider: Provider<PlaybackOrchestrator>,
        multiPanePlaybackPoolProvider: Provider<MultiPanePlaybackPool>,
        multiViewManagerProvider: Provider<MultiViewManager>
    ): AppPlayerLifecycleCoordinator =
        StartupDependencyProbe.traceCreate("AppPlayerLifecycleCoordinator") {
            AppPlayerLifecycleCoordinator(
                livePlayerManagerProvider.get(),
                previewPlayerManagerProvider.get(),
                pipControllerProvider.get(),
                playbackOrchestratorProvider.get(),
                multiPanePlaybackPoolProvider.get(),
                multiViewManagerProvider.get()
            )
        }
}
