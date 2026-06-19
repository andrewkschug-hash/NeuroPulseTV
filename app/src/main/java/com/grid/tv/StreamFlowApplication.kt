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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class StreamFlowApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            runDeferredStartup()
        }
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizeBytes(64 * 1024 * 1024)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .build()
                }
                .build()
        )
    }

    /**
     * Room and WorkManager init are expensive (WorkDatabase verification alone can take >1s).
     * Keep them off the main thread; WorkManager uses [workManagerConfiguration] on first access.
     */
    private fun runDeferredStartup() {
        AppDatabaseHolder.prewarm(this)
        Log.i(TAG, "AppDatabase prewarmed on background thread")
        val scheduler = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
            .epgScheduler()
        scheduler.scheduleAtLaunch()
        scheduler.runEpgRefreshNow()
        Log.i(TAG, "EPG workers scheduled on background thread")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "EpgFlow"
    }
}
