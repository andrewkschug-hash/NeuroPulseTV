package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ProfileMenuDropdown(
    expanded: Boolean,
    focusedIndex: Int,
    onDismiss: () -> Unit,
    onSwitchAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return
    val items = listOf(
        "Switch accounts" to onSwitchAccounts,
        "Settings" to onOpenSettings
    )
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = modifier
                .padding(top = 52.dp, end = 16.dp)
                .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(vertical = 6.dp)
        ) {
            items.forEachIndexed { index, (label, action) ->
                val focused = index == focusedIndex
                Surface(
                    onClick = {
                        action()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (focused) EpgColors.ChannelRowFocusBg else EpgColors.GridBg,
                        focusedContainerColor = EpgColors.ChannelRowFocusBg
                    )
                ) {
                    Text(
                        text = label,
                        color = if (focused) EpgColors.TextPrimary else EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (focused) {
                                    Modifier.border(2.dp, EpgColors.FocusBorder, RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}
