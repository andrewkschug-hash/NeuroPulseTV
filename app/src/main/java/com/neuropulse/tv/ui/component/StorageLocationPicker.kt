package com.neuropulse.tv.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.neuropulse.tv.feature.recording.StorageOption
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

private val DialogBg = Color(0xFF1A1A2E)
private val DialogTextSecondary = Color(0xFFB0B0C0)

@Composable
fun StorageLocationPicker(
    options: List<StorageOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Where should GRID save recordings?"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        titleContentColor = Color.White,
        textContentColor = DialogTextSecondary,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { option ->
                    Surface(
                        onClick = { onSelect(option.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EpgColors.BorderSubtle, RoundedCornerShape(8.dp)),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = EpgColors.ChannelColumnBg,
                            focusedContainerColor = EpgColors.ChannelRowFocusBg,
                            pressedContainerColor = EpgColors.ChannelRowFocusBg
                        )
                    ) {
                        Text(
                            text = option.displayLine(),
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent
                )
            ) {
                Text(
                    text = "Cancel",
                    color = EpgColors.TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    )
}

@Composable
fun RecordingPrecheckDialog(
    estimateText: String,
    lowStorageWarning: String?,
    insufficientSpaceWarning: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        titleContentColor = Color.White,
        textContentColor = DialogTextSecondary,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Start recording?",
                color = Color.White,
                fontFamily = DmSansFamily,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(estimateText, color = DialogTextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
                lowStorageWarning?.let {
                    Text(it, color = Color(0xFFFFB020), fontFamily = DmSansFamily, fontSize = 13.sp)
                }
                insufficientSpaceWarning?.let {
                    Text(it, color = Color(0xFFFF3B3B), fontFamily = DmSansFamily, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (insufficientSpaceWarning == null) {
                    Surface(
                        onClick = onConfirm,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = EpgColors.Accent,
                            focusedContainerColor = EpgColors.Accent.copy(alpha = 0.85f)
                        )
                    ) {
                        Text(
                            "Record",
                            color = Color.White,
                            fontFamily = DmSansFamily,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                ) {
                    Text(
                        "Cancel",
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    )
}
