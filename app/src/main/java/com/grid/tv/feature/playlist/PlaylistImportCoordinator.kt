package com.grid.tv.feature.playlist

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks active playlist imports so non-essential work (VOD refresh, recommendations)
 * can be deferred until import completes.
 */
@Singleton
class PlaylistImportCoordinator @Inject constructor() {
    private val activeImports = AtomicInteger(0)
    private val vodRefreshDeferred = AtomicBoolean(false)
    private val _importActive = MutableStateFlow(false)
    val importActive: StateFlow<Boolean> = _importActive.asStateFlow()

    fun beginImport(label: String) {
        val count = activeImports.incrementAndGet()
        _importActive.value = true
        Log.i(TAG, "IMPORT_STARTED label=$label activeCount=$count")
    }

    fun endImport(label: String): Boolean {
        val remaining = activeImports.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0) {
            activeImports.set(0)
            _importActive.value = false
            Log.i(TAG, "IMPORT_COMPLETED label=$label — all imports finished")
            return true
        }
        Log.i(TAG, "IMPORT_STEP_DONE label=$label remainingImports=$remaining")
        return false
    }

    fun isImportActive(): Boolean = activeImports.get() > 0

    fun deferVodRefresh(reason: String) {
        vodRefreshDeferred.set(true)
        Log.i(TAG, "VOD_REFRESH_DEFERRED reason=$reason")
    }

    fun consumeDeferredVodRefresh(): Boolean = vodRefreshDeferred.getAndSet(false)

    companion object {
        private const val TAG = "PlaylistImport"
    }
}
