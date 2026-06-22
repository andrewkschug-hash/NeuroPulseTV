package com.grid.tv.feature.epg

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Dedicated thread pool for EPG download, parse, and DB import.
 * Isolated from [kotlinx.coroutines.Dispatchers.IO] used by [com.grid.tv.feature.scanner.ChannelScanner]
 * so large playlist validation cannot starve EPG work (and vice versa).
 */
@Singleton
class EpgCoroutineDispatchers @Inject constructor() {
    private val threadCounter = AtomicInteger(0)

    private val threadFactory = ThreadFactory { runnable ->
        Thread(runnable, "epg-io-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val executor = Executors.newFixedThreadPool(POOL_SIZE, threadFactory)

    val io: CoroutineDispatcher = executor.asCoroutineDispatcher()

    companion object {
        const val POOL_SIZE = 1
    }
}
