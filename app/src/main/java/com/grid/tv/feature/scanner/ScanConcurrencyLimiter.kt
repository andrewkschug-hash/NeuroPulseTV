package com.grid.tv.feature.scanner

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ScanConcurrencyLimiter(initialLimit: Int = MAX_CONCURRENCY) {
    @Volatile
    private var limit = initialLimit.coerceIn(1, MAX_CONCURRENCY)

    private var semaphore = Semaphore(limit)

    fun updateLimit(newLimit: Int) {
        val safe = newLimit.coerceIn(1, MAX_CONCURRENCY)
        if (safe == limit) return
        limit = safe
        semaphore = Semaphore(safe)
    }

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }

    companion object {
        const val MAX_CONCURRENCY = 8
    }
}
