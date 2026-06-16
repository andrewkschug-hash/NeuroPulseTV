package com.grid.tv.ui.component

import android.util.Log
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

private const val FOCUS_LOG_TAG = "Focus"

/**
 * Requests focus without crashing when the target composable is not yet attached
 * (e.g. inside AnimatedVisibility, or before the first layout pass).
 */
fun FocusRequester.requestFocusSafely() {
    try {
        requestFocus()
    } catch (e: IllegalStateException) {
        Log.w(FOCUS_LOG_TAG, "requestFocus ignored: ${e.message}")
    }
}

/** Waits one frame for layout/canFocus, then [requestFocusSafely]. */
suspend fun FocusRequester.requestFocusSafelyAfterLayout(delayMs: Long = 0L) {
    withFrameMillis { }
    if (delayMs > 0L) {
        delay(delayMs)
    }
    requestFocusSafely()
}
