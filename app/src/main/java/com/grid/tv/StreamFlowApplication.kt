package com.grid.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.grid.tv.di.StartupEntryPoint
import com.grid.tv.feature.startup.StartupProfiler
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
        super.onCreate()
        StartupProfiler.mark("app_onCreate")
        LowEndDeviceMode.init(this)
        com.grid.tv.util.PlaybackDiagnostics.logDeviceProfile(this)
        PerformanceAudit.lowEndModeActive = !LowEndDeviceMode.current().performanceAuditEnabled
        val imageProfile = LowEndDeviceMode.current()
        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
            override fun onLowMemory() = trimImageCaches()
            override fun onTrimMemory(level: Int) {
                LowEndDeviceMode.onTrimMemory(level)
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    trimImageCaches()
                }
            }
        })
        Thread({
            runBlocking { runDeferredStartup() }
        }, "app-deferred-startup").start()
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

    private fun trimImageCaches() {
        runCatching {
            Coil.imageLoader(this).memoryCache?.clear()
        }
    }

    /**
     * Room and WorkManager init are expensive on large databases.
     * Keep them off the main thread; WorkManager uses [workManagerConfiguration] on first access.
     */
    private suspend fun runDeferredStartup() {
        val entryPoint = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
        entryPoint.startupOrchestrator().runColdStart(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}
