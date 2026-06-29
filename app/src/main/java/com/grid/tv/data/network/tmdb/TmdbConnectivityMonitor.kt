package com.grid.tv.data.network.tmdb

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.net.ssl.SSLException

/**
 * Tracks TMDB reachability for non-blocking UI warnings.
 * Catalog browsing continues when TMDB metadata/images are unavailable.
 */
@Singleton
class TmdbConnectivityMonitor @Inject constructor() {
    private val _warningMessage = MutableStateFlow<String?>(null)
    val warningMessage: StateFlow<String?> = _warningMessage.asStateFlow()

    fun recordSuccess() {
        _warningMessage.value = null
    }

    fun recordFailure(error: Throwable) {
        lastFailureMessage = error.message
        lastFailureWasSsl = error is SSLException || error.cause is SSLException
        _warningMessage.value = USER_WARNING
    }

    /** Last error detail for logcat via [TmdbService] — not shown in UI. */
    var lastFailureMessage: String? = null
        private set
    var lastFailureWasSsl: Boolean = false
        private set

    companion object {
        const val USER_WARNING =
            "Movie & series artwork is temporarily unavailable. Your catalog still works."
    }
}
