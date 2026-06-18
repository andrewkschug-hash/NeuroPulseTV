package com.grid.tv.util

/**
 * Compose [BasicTextField] nodes are not reported as Android text editors, so
 * [TvRemoteKeyboard.activateFocusedTextInput] cannot find them via [android.view.View.onCheckIsTextEditor].
 * Focused fields register here so [com.grid.tv.MainActivity] can open the IME on Enter / D-pad center.
 */
object TvFocusedTextInput {
    private var onActivate: (() -> Unit)? = null

    fun register(onActivate: () -> Unit) {
        this.onActivate = onActivate
    }

    fun unregister(onActivate: () -> Unit) {
        if (this.onActivate === onActivate) {
            this.onActivate = null
        }
    }

    fun activateCurrent(): Boolean {
        val handler = onActivate ?: return false
        handler()
        return true
    }
}
