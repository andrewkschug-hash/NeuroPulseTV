package com.grid.tv.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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

    private fun isImeNavigationKey(key: Key): Boolean =
        key == Key.DirectionUp ||
            key == Key.DirectionDown ||
            key == Key.DirectionLeft ||
            key == Key.DirectionRight ||
            key == Key.Enter ||
            key == Key.NumPadEnter ||
            key == Key.DirectionCenter

    /** @return true when parent handlers should not consume this key. */
    fun deferNavigationToIme(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return isImeNavigationKey(event.key)
    }

    /**
     * Consume D-pad / Enter in Compose while the IME is open so default focus traversal
     * does not jump between sibling fields. The IME still receives keys from the Activity.
     */
    fun consumesImeNavigationKeys(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return isImeNavigationKey(event.key)
    }

    /** Stand down all TV navigation while the IME owns D-pad input. */
    fun shouldStandDownNavigation(): Boolean = isActive

    /** Parent preview handlers must not consume these keys while text input is active. */
    fun shouldStandDownForActiveInput(event: KeyEvent): Boolean {
        if (!isActive || event.type != KeyEventType.KeyDown) return false
        return consumesImeNavigationKeys(event) ||
            event.key == Key.Back ||
            event.key == Key.Escape
    }
}

/** Block Compose focus traversal while a text field owns the IME session. */
fun Modifier.consumeImeNavigationKeysWhenTyping(): Modifier = onPreviewKeyEvent { event ->
    TvTextInputSession.consumesImeNavigationKeys(event)
}
