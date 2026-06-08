package com.neuropulse.tv.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.feature.recording.StorageOption
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

@Composable
fun StorageLocationPicker(
    options: List<StorageOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Where should GRID save recordings?"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 18.sp
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { option ->
                    Button(
                        onClick = { onSelect(option.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = option.displayLine(),
                            color = EpgColors.TextPrimary,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDismiss) {
                    Text("Cancel", color = EpgColors.TextSecondary)
                }
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
        title = {
            Text("Start recording?", color = EpgColors.TextPrimary, fontFamily = DmSansFamily)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(estimateText, color = EpgColors.TextSecondary, fontFamily = DmSansFamily, fontSize = 14.sp)
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
                    Button(onClick = onConfirm) { Text("Record") }
                }
                Button(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
