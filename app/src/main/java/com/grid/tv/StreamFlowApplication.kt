package com.grid.tv

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.grid.tv.data.db.AppDatabaseHolder
import com.grid.tv.di.StartupEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class StreamFlowApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Start Room open immediately on a background thread — do not wait for the coroutine dispatcher.
        Thread({
            runBlocking { runDeferredStartup() }
        }, "app-deferred-startup").start()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizeBytes(48 * 1024 * 1024)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .crossfade(200)
                .build()
        )
    }

    /**
     * Room and WorkManager init are expensive on large databases.
     * Keep them off the main thread; WorkManager uses [workManagerConfiguration] on first access.
     */
    private suspend fun runDeferredStartup() {
        AppDatabaseHolder.prewarm(this)
        Log.i(TAG, "AppDatabase prewarmed on background thread")
        val entryPoint = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
        entryPoint.startupCoordinator().warmCriticalLocalData()
        val scheduler = entryPoint.epgScheduler()
        scheduler.scheduleAtLaunch()
        scheduler.scheduleStartupEpg()
        Log.i(TAG, "Startup tasks scheduled (local UI warm, EPG delayed 5s, scanner deferred)")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "EpgFlow"
    }
}
