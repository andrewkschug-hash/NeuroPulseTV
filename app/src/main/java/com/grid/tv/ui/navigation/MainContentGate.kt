package com.grid.tv.ui.navigation

import android.widget.Toast
import com.grid.tv.di.PlayerEntryPoint
import com.grid.tv.ui.component.GlowFocusButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.feature.startup.StartupProfiler

@Composable
fun MainContentGate(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onSwitchProfile: () -> Unit,
    onRestartToOnboarding: () -> Unit,
    onSignOut: () -> Unit
) {
    var loadError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val playbackOrchestrator = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
            .playbackOrchestrator()
    }

    LaunchedEffect(playbackOrchestrator) {
        playbackOrchestrator.blockedMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            StartupProfiler.mark("ui_first_frame")
        }.onFailure {
            loadError = it.message ?: "Unable to load app"
        }
    }

    when {
        loadError != null -> NoServiceFallback(
            message = loadError!!,
            onRetry = { loadError = null },
            onConnect = { loadError = null }
        )
        else -> AppNavHost(
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onSwitchProfile = onSwitchProfile,
            onRestartToOnboarding = onRestartToOnboarding,
            onSignOut = onSignOut
        )
    }
}

@Composable
private fun NoServiceFallback(
    message: String,
    onRetry: () -> Unit,
    onConnect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Something went wrong",
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            RowButtons(onRetry = onRetry, onConnect = onConnect)
        }
    }
}

@Composable
private fun RowButtons(onRetry: () -> Unit, onConnect: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlowFocusButton(onClick = onRetry) { Text("Retry") }
        GlowFocusButton(onClick = onConnect) { Text("Continue") }
    }
}
