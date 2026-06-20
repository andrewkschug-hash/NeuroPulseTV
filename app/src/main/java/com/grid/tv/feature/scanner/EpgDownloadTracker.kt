package com.grid.tv.feature.scanner

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgDownloadTracker @Inject constructor() {
    private val inProgress = AtomicBoolean(false)

    fun setInProgress(active: Boolean) {
        inProgress.set(active)
    }

    fun isInProgress(): Boolean = inProgress.get()
}
