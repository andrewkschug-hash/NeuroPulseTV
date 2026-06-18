package com.grid.tv.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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

    /** @return true when parent handlers should not consume this key. */
    fun deferNavigationToIme(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionUp,
            Key.DirectionDown,
            Key.DirectionLeft,
            Key.DirectionRight,
            Key.Enter,
            Key.NumPadEnter,
            Key.DirectionCenter -> true
            else -> false
        }
    }

    /** Stand down all TV navigation while the IME owns D-pad input. */
    fun shouldStandDownNavigation(): Boolean = isActive

    /** Parent preview handlers must not consume these keys while text input is active. */
    fun shouldStandDownForActiveInput(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return deferNavigationToIme(event) ||
            event.key == Key.Back ||
            event.key == Key.Escape
    }
}
