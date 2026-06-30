package com.grid.tv.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/** Hoisted per-row focus targets owned by the screen and dispatched via [TvFocusDispatcher]. */
@Composable
fun rememberRowFocusRequesters(count: Int): List<FocusRequester> =
    remember(count) { List(count) { FocusRequester() } }
