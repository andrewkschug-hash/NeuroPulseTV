package com.grid.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.grid.tv.di.StartupEntryPoint
import com.grid.tv.di.WorkerFactoryEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors

@HiltAndroidApp
class StreamFlowApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        val scheduler = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
            .epgScheduler()
        scheduler.scheduleAtLaunch()
        scheduler.runEpgRefreshNow()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory())
            .build()

    private fun workerFactory(): HiltWorkerFactory =
        EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java)
            .workerFactory()
}
