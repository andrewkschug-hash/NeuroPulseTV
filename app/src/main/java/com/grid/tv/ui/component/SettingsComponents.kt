package com.grid.tv.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button as M3Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.ui.viewmodel.ConnectionDialogState

data class SettingsNavItem(
    val title: String,
    val subtitle: String
)

@Composable
fun SettingsSidebar(
    items: List<SettingsNavItem>,
    selectedIndex: Int,
    focusedIndex: Int,
    sidebarFocused: Boolean,
    itemFocusRequesters: List<FocusRequester>,
    onItemFocused: (Int) -> Unit,
    onSectionSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(EpgColors.ChannelColumnBg)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "Settings",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val focused = sidebarFocused && index == focusedIndex
            val bg = when {
                focused -> EpgColors.Accent.copy(alpha = 0.22f)
                selected -> EpgColors.ChannelRowFocusBg
                else -> Color.Transparent
            }
            GridFocusSurface(
                onClick = { onSectionSelected(index) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .then(
                        itemFocusRequesters.getOrNull(index)?.let { Modifier.focusRequester(it) }
                            ?: Modifier
                    )
                    .focusProperties { canFocus = true }
                    .focusable()
                    .onFocusChanged { if (it.isFocused) onItemFocused(index) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected || focused) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .background(EpgColors.Accent, RoundedCornerShape(2.dp))
                        )
                    }
                    Column(
                        modifier = Modifier.padding(start = if (selected || focused) 10.dp else 0.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = if (selected || focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp,
                            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = item.subtitle,
                            color = EpgColors.TextDimmed,
                            fontFamily = DmSansFamily,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(
    title: String,
    description: String? = null,
    cardIndex: Int? = null,
    focus: SettingsContentFocus? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // A card is highlighted when one of its controls currently holds focus. Scroll-into-view
    // is handled natively by `focusable()` inside the vertical scroll container, so the card
    // itself no longer drives scrolling (which used to fight the focused control's own scroll).
    val sectionHighlighted = cardIndex != null && focus != null && focus.isSectionHighlighted(cardIndex)
    val borderWidth = if (sectionHighlighted) 2.dp else 0.dp
    val borderColor = if (sectionHighlighted) EpgColors.Accent else Color.Transparent
    val backgroundColor = EpgColors.DetailPanelBg.copy(alpha = if (sectionHighlighted) 0.58f else 0.38f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (description != null) {
                Text(
                    text = description,
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp
                )
            }
        }
        content()
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    focused: Boolean = false,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    TvDialogTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = label,
        modifier = modifier,
        showFloatingLabel = false,
        backgroundColor = Color(0xFF252836),
        focusedBorderColor = if (focused) EpgColors.FocusBorder else Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedBorderWidth = if (focused) 2.dp else 0.dp,
        textColor = EpgColors.TextPrimary,
        placeholderColor = Color(0xFFB8BEC8),
        labelColor = EpgColors.TextSecondary,
        fieldHeight = if (singleLine) 44.dp else 96.dp
    )
}

@Composable
fun SettingsToggleRow(
    label: String,
    description: String? = null,
    enabled: Boolean,
    focused: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focused) {
                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .background(
                if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            description?.let {
                Text(
                    text = it,
                    color = EpgColors.TextDimmed,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsChip(
                label = if (enabled) "On" else "Off",
                selected = enabled,
                focused = focused
            )
            GlowFocusButton(
                onClick = onToggle,
                modifier = Modifier.focusProperties { canFocus = false }
            ) {
                Text(
                    text = if (enabled) "Turn off" else "Turn on",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SettingsChip(
    label: String,
    selected: Boolean = false,
    focused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderWidth = when {
        focused || selected -> 2.dp
        else -> 0.dp
    }
    val backgroundColor = when {
        selected -> EpgColors.Accent.copy(alpha = 0.22f)
        else -> Color(0xFF2E2E3E)
    }
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        selected -> EpgColors.Accent
        else -> Color.Transparent
    }
    Text(
        text = label,
        color = Color.White,
        fontFamily = DmSansFamily,
        fontSize = 13.sp,
        fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
fun SettingsListRow(
    title: String,
    subtitle: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isFocused) EpgColors.ChannelRowFocusBg else Color(0xFF252836),
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ProfileAvatarBadge(
    initials: String,
    colorHex: String,
    size: Int = 48,
    modifier: Modifier = Modifier
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(EpgColors.Accent)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontFamily = DmSansFamily,
            fontSize = (size / 2.4).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProfileColorPicker(
    colors: List<Color>,
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    swatchStartIndex: Int,
    focus: SettingsContentFocus,
    modifier: Modifier = Modifier
) {
    val normalizedSelected = selectedHex.trim().uppercase()
    Column(modifier = modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.chunked(4).forEachIndexed { rowIndex, rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowColors.forEachIndexed { colIndex, color ->
                    val hex = colorToHex(color)
                    val chainIndex = swatchStartIndex + rowIndex * 4 + colIndex
                    ProfileColorSwatch(
                        color = color,
                        selected = hex.uppercase() == normalizedSelected,
                        chainIndex = chainIndex,
                        focus = focus,
                        onClick = { onColorSelected(hex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileColorSwatch(
    color: Color,
    selected: Boolean,
    chainIndex: Int,
    focus: SettingsContentFocus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlighted = focus.isFocused(chainIndex)
    val canReceiveFocus = focus.isIndexInActiveCard(chainIndex)
    val ringColor = when {
        highlighted -> Color.White
        selected -> Color.White.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    val ringWidth = when {
        highlighted -> 3.dp
        selected -> 2.dp
        else -> 0.dp
    }

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .size(52.dp)
            .then(settingsFocusModifier(chainIndex, focus, enabled = canReceiveFocus))
            .focusable(enabled = canReceiveFocus)
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
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(ringWidth, ringColor, CircleShape)
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(color)
            )
            if (selected) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private val ConnectionDialogBg = Color(0xFF1A1A2E)
private val ConnectionDialogBody = Color(0xFFB0B0C0)

@Composable
fun ConnectionResultDialog(
    state: ConnectionDialogState,
    onDismiss: () -> Unit,
    onGoToGuide: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state) {
        confirmFocusRequester.requestFocusSafelyAfterLayout()
    }

    when (state) {
        ConnectionDialogState.Success -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = ConnectionDialogBg,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Connection successful",
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "Your playlist has been added. Channels are loading.",
                        color = ConnectionDialogBody,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    GlowFocusButton(
                        onClick = onGoToGuide,
                        modifier = Modifier.focusRequester(confirmFocusRequester)
                    ) {
                        Text(
                            "Go to Guide",
                            color = Color.White,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            )
        }
        is ConnectionDialogState.Failure -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = ConnectionDialogBg,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "Connection failed",
                        color = Color(0xFFFF5252),
                        fontFamily = DmSansFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "Could not connect to the provided URL. Please check your details and try again.\n\nError: ${state.errorMessage}",
                        color = ConnectionDialogBody,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    GlowFocusButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(confirmFocusRequester)
                    ) {
                        Text(
                            "Try again",
                            color = Color.White,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun FactoryResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Reset everything?",
    message: String = "This will delete all profiles, connections, watch history, favorites, and settings. The app will restart as if freshly installed. This cannot be undone.",
    confirmLabel: String = "Reset everything"
) {
    DestructiveConfirmDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun DestructiveConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Cancel"
) {
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        confirmFocusRequester.requestFocusSafelyAfterLayout()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConnectionDialogBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = Color(0xFFFF5252),
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                color = ConnectionDialogBody,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            M3Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                modifier = Modifier
                    .focusRequester(confirmFocusRequester)
                    .focusable()
            ) {
                Text(confirmLabel, color = Color.White)
            }
        },
        dismissButton = {
            M3TextButton(onClick = onDismiss) {
                Text(
                    dismissLabel,
                    color = ConnectionDialogBody,
                    fontFamily = DmSansFamily
                )
            }
        }
    )
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%06X", 0xFFFFFF and (r shl 16 or (g shl 8) or b))
}
