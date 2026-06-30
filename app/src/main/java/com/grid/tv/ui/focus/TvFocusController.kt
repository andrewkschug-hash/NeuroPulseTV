package com.grid.tv.ui.focus

import androidx.compose.ui.input.key.KeyEvent

/**
 * Contract for TV screen focus: zone transitions and D-pad handling live in one controller.
 * Imperative [androidx.compose.ui.focus.FocusRequester.requestFocus] calls belong in the screen's
 * single [TvFocusDispatcher], not in the controller.
 */
interface TvFocusController<Zone : Enum<Zone>> {
    val focusZone: Zone

    fun transitionToZone(zone: Zone, detail: String = "")

    /** Returns true when the key was consumed. Each D-pad event must be handled at most once per screen. */
    fun handleKey(event: KeyEvent): Boolean
}
