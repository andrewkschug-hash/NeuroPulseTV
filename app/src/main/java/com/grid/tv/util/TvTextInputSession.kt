package com.grid.tv.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks when a text field is actively accepting IME input.
 * Parent TV navigation must stand down so D-pad events reach the on-screen keyboard.
 */
object TvTextInputSession {
    private val depth = AtomicInteger(0)

    val isActive: Boolean
        get() = depth.get() > 0

    fun begin() {
        depth.incrementAndGet()
    }

    fun end() {
        depth.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    /** @return true when Compose should consume this key so it does not reach background fields. */
    fun consumesImeNavigationKeys(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return TvImeKeyDispatcher.isImeNavigationKey(event.key)
    }

    /** Parent preview handlers must not consume Back while text input is active. */
    fun shouldStandDownForActiveInput(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return consumesImeNavigationKeys(event) ||
            event.key == Key.Back ||
            event.key == Key.Escape
    }
}

/** Consume D-pad / Enter in Compose while the IME is open so focus stays on the keyboard. */
fun Modifier.consumeImeKeysWhenVisible(): Modifier = onPreviewKeyEvent { event ->
    TvTextInputSession.consumesImeNavigationKeys(event)
}

/** Prevent Compose from moving focus to sibling fields while the IME is open. */
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
