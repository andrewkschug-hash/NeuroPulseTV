package com.grid.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.grid.tv.di.WorkerFactoryEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors

@HiltAndroidApp
class StreamFlowApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory())
            .build()

    private fun workerFactory(): HiltWorkerFactory =
        EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java)
            .workerFactory()
}
