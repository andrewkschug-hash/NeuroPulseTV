package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

private val MenuWidth = 220.dp
private val MenuBg = Color(0xFF1E1E2E)
private val MenuShape = RoundedCornerShape(10.dp)
private val RowShape = RoundedCornerShape(8.dp)

@Composable
fun ProfileMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSwitchAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") focusedIndex: Int = 0
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = modifier
                .width(MenuWidth)
                .padding(top = EpgLayout.TopBarHeight + 4.dp, end = 16.dp)
                .background(MenuBg, MenuShape)
                .border(1.dp, EpgColors.BorderSubtle, MenuShape)
                .padding(vertical = 4.dp)
        ) {
            ProfileMenuDropdownItem(
                label = "Switch accounts",
                onClick = {
                    onDismiss()
                    onSwitchAccounts()
                }
            )
            ProfileMenuDropdownItem(
                label = "Settings",
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )
        }
    }
}

@Composable
private fun ProfileMenuDropdownItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) EpgColors.FocusBorder else Color.Transparent,
                shape = RowShape
            )
            .background(
                color = if (isFocused) EpgColors.ChannelRowFocusBg else Color.Transparent,
                shape = RowShape
            ),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        )
    ) {
        Text(
            text = label,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
