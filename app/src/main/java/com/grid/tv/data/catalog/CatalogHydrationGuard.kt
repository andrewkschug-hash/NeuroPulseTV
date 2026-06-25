package com.grid.tv.data.catalog

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defers JSON/network catalog hydration and bulk SQLite ingest while live playback
 * is active so tune/zap stays responsive and clear of parse/GC spikes.
 */
@Singleton
class CatalogHydrationGuard @Inject constructor() {
    @Volatile
    var viewportEpgSuspended: Boolean = false
        private set

    fun setViewportEpgSuspended(suspended: Boolean) {
        viewportEpgSuspended = suspended
    }

    fun shouldDeferHeavyCatalogIo(): Boolean = viewportEpgSuspended
}
