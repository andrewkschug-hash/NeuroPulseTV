package com.grid.tv.util

import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager

/**
 * TV remotes often need an explicit IME request on the focused editor — focus alone
 * does not open the keyboard on Android TV / Fire TV / Chromecast.
 */
object TvRemoteKeyboard {
    fun isActivateKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER

    /**
     * @return true when a focused text field was found and the keyboard was requested.
     */
    fun activateFocusedTextInput(rootView: View): Boolean {
        if (TvFocusedTextInput.activateCurrent()) return true

        val focused = rootView.findFocus() ?: return false
        if (focused.onCheckIsTextEditor()) {
            requestIme(rootView, focused)
            return true
        }
        return false
    }

    fun dismissKeyboard(rootView: View) {
        val imm = rootView.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }

    internal fun requestIme(rootView: View, target: View = rootView.findFocus() ?: rootView) {
        rootView.context.findComponentActivity()?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        target.requestFocus()
        target.post {
            val imm = rootView.context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(target, InputMethodManager.SHOW_FORCED)
            if (imm?.isActive(target) != true) {
                imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
}
