package com.grid.tv.util

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * TV remotes often need an explicit [View.performClick] on the focused editor — focus alone
 * does not open the IME on Android TV / Fire TV / Chromecast.
 */
object TvRemoteKeyboard {
    fun isActivateKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER

    /**
     * @return true when the currently focused view is a text editor and the keyboard was requested.
     */
    fun activateFocusedTextInput(rootView: View): Boolean {
        val focused = rootView.findFocus() ?: return false
        if (!focused.onCheckIsTextEditor()) return false
        focused.performClick()
        focused.requestFocus()
        val imm = rootView.context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT)
        if (imm?.isActive(focused) != true) {
            imm?.showSoftInput(focused, InputMethodManager.SHOW_FORCED)
        }
        return true
    }
}
