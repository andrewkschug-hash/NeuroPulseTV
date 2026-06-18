package com.grid.tv.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.grid.tv.util.TvFocusedTextInput

/** Registers a TV text field so Enter / D-pad center opens the IME from [com.grid.tv.MainActivity]. */
@Composable
fun TvTextInputActivationEffect(
    active: Boolean,
    onActivate: () -> Unit
) {
    DisposableEffect(active, onActivate) {
        if (active) {
            TvFocusedTextInput.register(onActivate)
            onDispose { TvFocusedTextInput.unregister(onActivate) }
        } else {
            onDispose {}
        }
    }
}
