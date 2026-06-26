package com.grid.tv.feature.scanner

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Serializes channel validation HTTP so IPTV providers are not hammered with parallel tune requests.
 */
class ScanConcurrencyLimiter(initialLimit: Int = DEFAULT_CONCURRENCY) {
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
    /** Strict single-flight probing — avoids 520 / multi-login triggers from rapid channel switching. */
    const val DEFAULT_CONCURRENCY = 1
    const val MAX_CONCURRENCY = 1
    const val INTER_PROBE_DELAY_MS = 300L
  }
}
