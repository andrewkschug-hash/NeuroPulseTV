package com.grid.tv.ui.platform

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val MinimumTouchTarget = 48.dp

/**
 * Ensures interactive elements meet Material touch target guidance on phones/tablets.
 */
@Composable
fun Modifier.touchTarget(): Modifier {
    val formFactor = LocalDeviceFormFactor.current
    return if (formFactor.enableTouchGestures) {
        defaultMinSize(minWidth = MinimumTouchTarget, minHeight = MinimumTouchTarget)
    } else {
        this
    }
}
