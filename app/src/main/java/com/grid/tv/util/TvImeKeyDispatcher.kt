package com.grid.tv.util

import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Forwards remote D-pad / Enter to the active IME without blocking it in Compose. */
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

    fun forwardToIme(view: View, event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !isImeNavigationKey(event.key)) return false
        return forwardToIme(view, event.nativeKeyEvent)
    }

    fun forwardToIme(view: View, event: AndroidKeyEvent): Boolean {
        if (event.action != AndroidKeyEvent.ACTION_DOWN || !isImeNavigationKeyCode(event.keyCode)) {
            return false
        }
        val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (imm.dispatchInputEvent(event)) return true
        }
        @Suppress("DEPRECATION")
        return imm.sendKeyEvent(event)
    }
}
