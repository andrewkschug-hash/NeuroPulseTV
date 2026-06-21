package com.grid.tv.ui.component

import android.util.Log
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val FOCUS_LOG_TAG = "TvFocus"

/**
 * Requests focus without crashing when the target composable is not yet attached,
 * already removed from composition, or the parent focus tree is mid-transition
 * (e.g. back navigation while [requestFocusSafelyAfterLayout] is in flight).
 */
fun FocusRequester.requestFocusSafely(): Boolean {
    val result = runCatching { requestFocus() }
    result.exceptionOrNull()?.let { error ->
        if (error is CancellationException) throw error
        Log.w(
            FOCUS_LOG_TAG,
            "requestFocus ignored (${error.javaClass.simpleName}): ${error.message}"
        )
    }
    return result.isSuccess
}

/** Waits one frame for layout/canFocus, then [requestFocusSafely]. */
suspend fun FocusRequester.requestFocusSafelyAfterLayout(delayMs: Long = 0L): Boolean {
    withFrameMillis { }
    if (!currentCoroutineContext().isActive) return false
    if (delayMs > 0L) {
        delay(delayMs)
        if (!currentCoroutineContext().isActive) return false
    }
    return requestFocusSafely()
}
