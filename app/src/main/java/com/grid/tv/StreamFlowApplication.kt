package com.grid.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.grid.tv.di.StartupEntryPoint
import com.grid.tv.di.MemoryEntryPoint
import com.grid.tv.data.db.AppDatabaseHolder
import com.grid.tv.feature.startup.MemoryPressureHandler
import com.grid.tv.feature.startup.MemoryPressureMonitor
import com.grid.tv.feature.startup.StartupProfiler
import com.grid.tv.feature.startup.StartupTiming
import com.grid.tv.feature.startup.StartupTrace
import com.grid.tv.player.LowEndDeviceMode
import com.grid.tv.util.PosterImageEventListener
import com.grid.tv.util.PerformanceAudit
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class StreamFlowApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        StartupTiming.markProcessStart()
        StartupTiming.trace("AppDatabaseHolder.earlyPrewarm") {
            Thread({ AppDatabaseHolder.prewarm(this) }, "db-prewarm-early").start()
        }
        StartupTiming.log("Application.onCreate() start")
        StartupTiming.trace("Application.super.onCreate (Hilt Application inject)") {
            super.onCreate()
        }
        StartupTiming.trace("MemoryPressureMonitor.start") {
            MemoryPressureMonitor.start()
        }
        StartupProfiler.mark("app_onCreate")
        StartupTiming.trace("LowEndDeviceMode.init") {
            LowEndDeviceMode.init(this)
        }
        StartupTiming.trace("PlaybackDiagnostics.logDeviceProfile") {
            com.grid.tv.util.PlaybackDiagnostics.logDeviceProfile(this)
        }
        PerformanceAudit.lowEndModeActive = !LowEndDeviceMode.current().performanceAuditEnabled
        val imageProfile = LowEndDeviceMode.current()
        StartupTiming.trace("registerComponentCallbacks") {
            registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
                override fun onLowMemory() = memoryPressureHandler().onTrimMemory(
                    this@StreamFlowApplication,
                    android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                )
                override fun onTrimMemory(level: Int) {
                    LowEndDeviceMode.onTrimMemory(level)
                    memoryPressureHandler().onTrimMemory(this@StreamFlowApplication, level)
                }
            })
        }
        StartupTiming.trace("Deferred startup thread launch") {
            Thread({
                StartupTiming.log("app-deferred-startup thread running")
                runBlocking { runDeferredStartup() }
            }, "app-deferred-startup").start()
        }
        StartupTiming.log("Deferred startup launched")
        StartupTiming.trace("Coil initialization") {
            Coil.setImageLoader(
                ImageLoader.Builder(this)
                    .memoryCache {
                        MemoryCache.Builder(this)
                            .maxSizeBytes(imageProfile.coilMemoryCacheBytes.toInt())
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(cacheDir.resolve("image_cache"))
                            .maxSizeBytes(imageProfile.coilDiskCacheBytes)
                            .build()
                    }
                    .crossfade(if (imageProfile.active) 0 else 200)
                    .allowRgb565(imageProfile.active)
                    .eventListenerFactory(PosterImageEventListener.Factory)
                    .build()
            )
        }
        StartupTiming.log("Application.onCreate() complete")
    }

    private fun memoryPressureHandler(): MemoryPressureHandler =
        EntryPointAccessors.fromApplication(this, MemoryEntryPoint::class.java)
            .memoryPressureHandler()

    /**
     * Room and WorkManager init are expensive on large databases.
     * Keep them off the main thread; WorkManager uses [workManagerConfiguration] on first access.
     */
    private suspend fun runDeferredStartup() {
        val entryPoint = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
        StartupTrace.traceSuspend("StartupOrchestrator.runColdStart") {
            entryPoint.startupOrchestrator().runColdStart(this)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
