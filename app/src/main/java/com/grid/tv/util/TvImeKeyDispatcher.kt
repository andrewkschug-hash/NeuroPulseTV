package com.grid.tv.util

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Identifies remote keys that should reach the on-screen IME instead of TV focus chains. */
object TvImeKeyDispatcher {
    fun isImeNavigationKey(key: Key): Boolean =
        key == Key.DirectionUp ||
            key == Key.DirectionDown ||
            key == Key.DirectionLeft ||
            key == Key.DirectionRight ||
            key == Key.Enter ||
            key == Key.NumPadEnter ||
            key == Key.DirectionCenter

    fun isImeNavigationKeyCode(keyCode: Int): Boolean =
        keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER

    /**
     * Do not consume IME navigation keys in Compose — [android.app.Activity.dispatchKeyEvent]
     * delivers them to the IME when [TvTextInputSession] is active.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shouldPassToIme(view: View, event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown && isImeNavigationKey(event.key)

    @Suppress("UNUSED_PARAMETER")
    fun shouldPassToIme(view: View, event: AndroidKeyEvent): Boolean =
        event.action == AndroidKeyEvent.ACTION_DOWN && isImeNavigationKeyCode(event.keyCode)
}
