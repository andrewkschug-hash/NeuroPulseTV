package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private val ModalBg = Color(0xFF14141E)
private val ModalBorder = Color.White.copy(alpha = 0.08f)

@Composable
fun GridModal(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 480.dp,
    showCloseButton: Boolean = true,
    focusCloseButtonOnOpen: Boolean = false,
    closeFocusRequester: FocusRequester = remember { FocusRequester() },
    content: @Composable ColumnScope.() -> Unit
) {
    LaunchedEffect(showCloseButton, focusCloseButtonOnOpen) {
        if (showCloseButton && focusCloseButtonOnOpen) {
            closeFocusRequester.requestFocusSafelyAfterLayout()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .clip(RoundedCornerShape(16.dp))
                .background(ModalBg)
                .border(1.dp, ModalBorder, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            if (showCloseButton) {
                GridModalCloseButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .focusRequester(closeFocusRequester)
                )
            }
            Column(
                modifier = Modifier.padding(24.dp),
                content = content
            )
        }
    }
}

@Composable
private fun GridModalCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .size(36.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = focused,
                shape = RoundedCornerShape(8.dp),
                unfocusedColor = Color.Transparent
            )
            .semantics { contentDescription = "Close" },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color(0xFF2A2A3A)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 22.sp
            )
        }
    }
}
