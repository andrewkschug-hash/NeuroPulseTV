package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.grid.tv.ui.platform.touchTarget
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

private val ButtonHeight = 44.dp
private val ButtonHorizontalPadding = 12.dp
private val PrimaryBlue = Color(0xFF3B8FFF)
private val PrimaryBlueFocus = Color(0xFF5AA3FF)
private val OutlinedBorder = Color(0xFF4B5563)

@Composable
fun GridPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) PrimaryBlueFocus else PrimaryBlue

    val buttonShape = RoundedCornerShape(8.dp)
    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ButtonHeight)
            .touchTarget()
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(
                focused = focused,
                shape = buttonShape,
                unfocusedColor = Color.Transparent
            )
            .semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(buttonShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg,
            pressedContainerColor = bg,
            disabledContainerColor = bg.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = ButtonHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GridOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) EpgColors.FocusBorder else OutlinedBorder
    val textColor = if (focused) Color.White else Color(0xFF9CA3AF)

    GridFocusSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ButtonHeight)
            .touchTarget()
            .onFocusChanged { focused = it.isFocused }
            .semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF1E1E2E)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .tvFocusBorder(focused = focused, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = ButtonHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GridGhostLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = text
) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Color.White else Color(0xFF9CA3AF)

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        )
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
