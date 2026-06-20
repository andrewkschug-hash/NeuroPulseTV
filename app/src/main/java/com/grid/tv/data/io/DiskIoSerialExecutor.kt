package com.grid.tv.data.io

import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Single-thread executor for heavy cache / JSON / spool writes.
 * Serializes disk work so EPG spool, VOD cache, and Coil disk cache do not stampede storage I/O.
 */
@Singleton
class DiskIoSerialExecutor @Inject constructor() {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "disk-io-serial").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
}
