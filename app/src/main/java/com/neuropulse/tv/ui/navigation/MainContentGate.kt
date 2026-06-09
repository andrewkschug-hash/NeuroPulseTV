package com.neuropulse.tv.ui.navigation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import kotlinx.coroutines.delay

@Composable
fun MainContentGate(
    onPickLocalFile: () -> Unit,
    onPickTiviMateZip: () -> Unit,
    onSwitchProfile: () -> Unit
) {
    var ready by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            delay(50)
            ready = true
        }.onFailure {
            loadError = it.message ?: "Unable to load app"
        }
    }

    when {
        loadError != null -> NoServiceFallback(
            message = loadError!!,
            onRetry = { loadError = null; ready = false },
            onConnect = { ready = true; loadError = null }
        )
        !ready -> LoadingPlaceholder()
        else -> AppNavHost(
            onPickLocalFile = onPickLocalFile,
            onPickTiviMateZip = onPickTiviMateZip,
            onSwitchProfile = onSwitchProfile
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EpgColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading…",
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 16.sp
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
        Button(onClick = onRetry) { Text("Retry") }
        Button(onClick = onConnect) { Text("Continue") }
    }
}
