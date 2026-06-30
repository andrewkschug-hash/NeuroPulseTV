package com.grid.tv.ui.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.grid.tv.util.TvTextInputSession

/**
 * Single root focus/key entry for a TV screen. All D-pad and Back handling flows through
 * [onKey] exactly once per event (VOD Hub pattern).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvScreenFocusRoot(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    standDownForIme: Boolean = true,
    onBack: (() -> Boolean)? = null,
    onKey: (KeyEvent) -> Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (!enabled) return@onPreviewKeyEvent false
            if (standDownForIme && TvTextInputSession.shouldStandDownForActiveInput(event)) {
                return@onPreviewKeyEvent false
            }
            if (
                event.type == KeyEventType.KeyDown &&
                (event.key == Key.Back || event.key == Key.Escape) &&
                onBack != null
            ) {
                return@onPreviewKeyEvent onBack()
            }
            onKey(event)
        },
        content = content,
    )
}
