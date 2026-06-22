package com.grid.tv.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.data.update.AppUpdateInfo
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

@Composable
fun UpdateAvailableDialog(
    update: AppUpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val laterFocusRequester = remember { FocusRequester() }
    val downloadFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        downloadFocusRequester.requestFocusSafelyAfterLayout()
    }

    GridModal(
        onDismiss = onDismiss,
        showCloseButton = false,
        width = 520.dp
    ) {
        Text(
            text = "Update available",
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Version ${update.versionName} is available. You are on $currentVersion.",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.fillMaxWidth()
        )
        update.releaseNotes?.let { notes ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = notes.take(500).let { if (notes.length > 500) "$it…" else it },
                color = EpgColors.TextDimmed,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GridOutlinedButton(
                text = "Later",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(laterFocusRequester)
            )
            GridPrimaryButton(
                text = "Download",
                onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                        context.startActivity(intent)
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(downloadFocusRequester)
            )
        }
    }
}
