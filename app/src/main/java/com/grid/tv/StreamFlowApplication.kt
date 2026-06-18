package com.grid.tv

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.grid.tv.di.StartupEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

@HiltAndroidApp
class StreamFlowApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        Log.i(TAG, "WorkManager initialized with HiltWorkerFactory")
        val scheduler = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
            .epgScheduler()
        scheduler.scheduleAtLaunch()
        scheduler.runEpgRefreshNow()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "EpgFlow"
    }
}
