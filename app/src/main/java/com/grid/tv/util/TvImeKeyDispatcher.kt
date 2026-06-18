package com.grid.tv.util

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key

/** Identifies remote keys routed to the on-screen IME while a text field is active. */
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
}
