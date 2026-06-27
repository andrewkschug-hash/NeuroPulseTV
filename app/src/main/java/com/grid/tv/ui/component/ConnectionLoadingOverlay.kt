package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

/**
 * Full-screen feedback while Xtream / M3U / Stalker catalog import runs (can take minutes).
 */
@Composable
fun ConnectionLoadingOverlay(
    message: String = "Connecting to your provider…",
    subtitle: String = "Loading channels and guide data. This may take a few minutes.",
    modifier: Modifier = Modifier,
    accentColor: Color = EpgColors.Accent
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(52.dp),
            color = accentColor,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(28.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .height(5.dp),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = message,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            color = EpgColors.TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}
