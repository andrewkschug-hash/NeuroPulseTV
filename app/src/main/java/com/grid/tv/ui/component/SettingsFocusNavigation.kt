package com.grid.tv.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvTextInputSession

enum class SettingsFocusPanel { TOP_BAR, LIST, DETAIL }

/** Shared corner radius for interactive settings rows and action chips. */
val SettingsRowShape = RoundedCornerShape(10.dp)

private val PillShape = RoundedCornerShape(6.dp)
private val PillBackground = Color(0xFF2E2E3E)

/**
 * Standard TV focus wiring for settings controls: focusable, scroll-into-view, optional entry requester.
 */
@Composable
fun Modifier.settingsInteractiveFocus(
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onFocusChanged: ((Boolean) -> Unit)? = null
): Modifier {
    var modifier = this
    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }
    modifier = modifier
        .focusable(enabled = enabled)
        .tvFocusScrollIntoView(enabled = enabled)
    if (onFocusChanged != null) {
        modifier = modifier.onFocusChanged { onFocusChanged(it.isFocused) }
    }
    return modifier
}

@Composable
fun SettingsFocusTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
    nextFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = label,
        singleLine = singleLine,
        isPassword = isPassword,
        enabled = enabled,
        focusRequester = focusRequester,
        nextFocusRequester = nextFocusRequester,
        imeAction = imeAction,
        onImeNext = onImeNext,
        modifier = modifier.tvFocusScrollIntoView(enabled = enabled)
    )
}

@Composable
fun SettingsFocusPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var highlighted by remember { mutableStateOf(false) }
    val borderWidth = when {
        highlighted -> 2.dp
        selected -> 2.dp
        else -> 0.dp
    }
    val backgroundColor = when {
        selected -> EpgColors.Accent.copy(alpha = 0.22f)
        else -> PillBackground
    }
    val borderColor = when {
        highlighted -> EpgColors.FocusBorder
        selected -> EpgColors.Accent
        else -> Color.Transparent
    }
    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .settingsInteractiveFocus(
                focusRequester = focusRequester,
                onFocusChanged = {
                    highlighted = it
                    onFocusChanged?.invoke(it)
                }
            )
            .tvFocusBorder(
                focused = highlighted,
                shape = PillShape,
                unfocusedColor = Color.Transparent
            )
            .border(borderWidth, borderColor, PillShape)
            .onPreviewKeyEvent { event ->
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (highlighted) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = backgroundColor,
            pressedContainerColor = backgroundColor.copy(alpha = 0.85f),
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f)
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                fontWeight = if (selected || highlighted) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SettingsFocusPillGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            SettingsFocusPill(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                focusRequester = if (index == 0) entryFocusRequester else null
            )
        }
    }
}

@Composable
fun SettingsFocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingLabel: String = "Connecting...",
    destructive: Boolean = false
) {
    var highlighted by remember { mutableStateOf(false) }
    GlowFocusButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        externallyFocused = highlighted,
        modifier = modifier
            .settingsInteractiveFocus(
                focusRequester = focusRequester,
                enabled = enabled && !isLoading,
                onFocusChanged = {
                    highlighted = it
                    onFocusChanged?.invoke(it)
                }
            )
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    loadingLabel,
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp
                )
            }
        } else {
            Text(
                text = text,
                color = if (destructive) Color(0xFFE53935) else Color.White,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SettingsFocusProfileRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    SettingsListRow(
        title = title,
        subtitle = subtitle,
        isFocused = isFocused,
        modifier = modifier
            .settingsInteractiveFocus(
                focusRequester = focusRequester,
                onFocusChanged = { isFocused = it }
            )
            .onPreviewKeyEvent { event ->
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (isFocused && !isActive) {
                            onSelect()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
        trailing = {
            if (isActive) {
                Text(
                    text = "Active",
                    color = EpgColors.Accent,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
fun SettingsFocusToggleRow(
    label: String,
    description: String? = null,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var highlighted by remember { mutableStateOf(false) }
    SettingsToggleRow(
        label = label,
        description = description,
        enabled = enabled,
        focused = highlighted,
        onToggle = onToggle,
        modifier = modifier
            .settingsInteractiveFocus(
                focusRequester = focusRequester,
                onFocusChanged = { highlighted = it }
            )
            .onPreviewKeyEvent { event ->
                if (TvTextInputSession.shouldStandDownForActiveInput(event)) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (highlighted) {
                            onToggle()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    )
}
