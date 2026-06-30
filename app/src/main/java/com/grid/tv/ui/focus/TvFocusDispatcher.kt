package com.grid.tv.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import com.grid.tv.ui.component.requestFocusSafelyAfterLayout

/**
 * Single screen-level focus dispatcher. All [FocusRequester.requestFocus] calls for a screen
 * should flow through one [TvFocusDispatcher] instance.
 */
@Composable
fun TvFocusDispatcher(
    enabled: Boolean,
    vararg keys: Any?,
    requestFocus: suspend () -> Unit,
) {
    LaunchedEffect(enabled, *keys) {
        if (!enabled) return@LaunchedEffect
        requestFocus()
    }
}

/** Convenience wrapper for dispatching to one requester after layout. */
suspend fun FocusRequester.dispatchFocus() {
    requestFocusSafelyAfterLayout()
}
