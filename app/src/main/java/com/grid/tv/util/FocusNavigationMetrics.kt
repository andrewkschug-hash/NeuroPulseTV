package com.grid.tv.util

import android.util.Log

/**
 * Tracks focus-driven side effects. Focus navigation must stay UI-only — no network or heavy work.
 */
object FocusNavigationMetrics {
    private const val TAG = "FocusNavMetrics"

    fun onFocusUiOnly(label: String, channelIndex: Int = -1) {
        PerformanceAudit.logFocusNavigation(label, channelIndex)
    }

    fun reportBlockedSideEffect(label: String, reason: String) {
        Log.w(TAG, "Blocked focus side-effect label=$label reason=$reason")
        PerformanceAudit.logFocusSideEffectBlocked(label, reason)
    }
}
