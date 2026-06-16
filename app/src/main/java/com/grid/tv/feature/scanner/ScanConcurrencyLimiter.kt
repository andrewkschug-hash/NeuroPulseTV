package com.grid.tv.feature.scanner

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ScanConcurrencyLimiter(initialLimit: Int = 10) {
    @Volatile
    private var limit = initialLimit.coerceAtLeast(1)

    private var semaphore = Semaphore(limit)

    fun updateLimit(newLimit: Int) {
        val safe = newLimit.coerceAtLeast(1)
        if (safe == limit) return
        limit = safe
        semaphore = Semaphore(safe)
    }

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
}
