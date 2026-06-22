package com.grid.tv.ui.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button as M3Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grid.tv.feature.update.AppUpdateInfo
import com.grid.tv.ui.theme.DmSansFamily

private val DialogBg = Color(0xFF1A1A2E)
private val DialogBody = Color(0xFFB0B0C0)

@Composable
fun UpdateAvailableDialog(
    info: AppUpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val updateFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        updateFocus.requestFocusSafelyAfterLayout()
    }

    val notesPreview = info.releaseNotes
        ?.lineSequence()
        ?.filter { it.isNotBlank() }
        ?.take(6)
        ?.joinToString("\n")
        ?.let { if (it.isNotBlank()) "\n\n$it" else "" }
        .orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Update available",
                color = Color(0xFF3B8FFF),
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "GRID v${info.latestVersion} is available. You are on an older version.$notesPreview",
                color = DialogBody,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            M3Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B8FFF)),
                modifier = Modifier
                    .focusRequester(updateFocus)
                    .focusable()
            ) {
                Text("Download update", color = Color.White, fontFamily = DmSansFamily)
            }
        },
        dismissButton = {
            M3Button(onClick = onDismiss) {
                Text("Later", fontFamily = DmSansFamily)
            }
        }
    )
}
