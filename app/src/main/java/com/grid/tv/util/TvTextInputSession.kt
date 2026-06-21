package com.grid.tv.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.type
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks when a text field is actively accepting IME input.
 *
 * D-pad keys must reach the on-screen keyboard (via [android.app.Activity.dispatchKeyEvent]),
 * so Compose handlers must **not** consume them. Background form navigation is blocked with
 * [lockFocusWhileTyping] instead.
 */
object TvTextInputSession {
    private val depth = AtomicInteger(0)
    private val compositionActive = mutableStateOf(false)

    val isActive: Boolean
        get() = depth.get() > 0

    /** Observe in Compose to disable background focus while the IME dialog is open. */
    val isActiveState = compositionActive

    fun begin() {
        depth.incrementAndGet()
        compositionActive.value = true
    }

    fun end() {
        depth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        compositionActive.value = depth.get() > 0
    }

    /**
     * @return true when TV focus-chain / screen handlers should stand down (return false, do not
     * consume) so the key can reach the IME. Back is included so the active field can dismiss.
     */
    fun shouldStandDownForActiveInput(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return TvImeKeyDispatcher.isImeNavigationKey(event.key) ||
            event.key == Key.Back ||
            event.key == Key.Escape
    }
}

/** Block Compose focus traversal to sibling fields while the IME is open. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.lockFocusWhileTyping(active: Boolean): Modifier =
    if (!active) {
        this
    } else {
        focusProperties {
            up = FocusRequester.Cancel
            down = FocusRequester.Cancel
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
        }
    }
