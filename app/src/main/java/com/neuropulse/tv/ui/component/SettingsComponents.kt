package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .background(bg, RoundedCornerShape(8.dp))
                    .then(
                        if (focused) {
                            Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
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
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
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

@Composable
fun SettingsPanel(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EpgColors.DetailPanelBg.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(10.dp))
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
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(EpgColors.Accent),
            modifier = Modifier
                .fillMaxWidth()
                .background(EpgColors.GridBg, RoundedCornerShape(8.dp))
                .then(
                    if (focused) {
                        Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                    } else {
                        Modifier.border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = EpgColors.TextDimmed,
                            fontFamily = DmSansFamily,
                            fontSize = 14.sp
                        )
                    }
                    inner()
                }
            }
        )
    }
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
            Button(onClick = onToggle) {
                Text(if (enabled) "Turn off" else "Turn on")
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
    val bg = when {
        focused -> EpgColors.Accent.copy(alpha = 0.25f)
        selected -> EpgColors.ChannelRowFocusBg
        else -> EpgColors.GridBg
    }
    val borderColor = when {
        focused -> EpgColors.FocusBorder
        selected -> EpgColors.Accent.copy(alpha = 0.5f)
        else -> EpgColors.BorderSubtle
    }
    Text(
        text = label,
        color = if (focused || selected) EpgColors.TextPrimary else EpgColors.TextSecondary,
        fontFamily = DmSansFamily,
        fontSize = 13.sp,
        fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(bg, RoundedCornerShape(6.dp))
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
                if (isFocused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(8.dp))
                } else {
                    Modifier.border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
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
    modifier: Modifier = Modifier
) {
    val normalizedSelected = selectedHex.trim().uppercase()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.chunked(4).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowColors.forEach { color ->
                    val hex = colorToHex(color)
                    ProfileColorSwatch(
                        color = color,
                        selected = hex.uppercase() == normalizedSelected,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val ringColor = when {
        selected -> Color.White
        focused -> EpgColors.FocusBorder
        else -> Color.Transparent
    }
    val ringWidth = if (selected || focused) 2.dp else 0.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(ringWidth, ringColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(color)
            )
            if (selected) {
                Text(text = "✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%06X", 0xFFFFFF and (r shl 16 or (g shl 8) or b))
}
