package com.grid.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card

@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "focusScale")

    Card(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .border(
                BorderStroke(if (focused) 2.dp else 0.dp, if (focused) Color(0xFF1E90FF) else Color.Transparent),
                RoundedCornerShape(12.dp)
            ),
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(10.dp)) {
            content()
        }
    }
}
