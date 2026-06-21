package com.grid.tv.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.player.StreamFailoverUiState
import com.grid.tv.ui.theme.DmSansFamily

@Composable
fun StreamFailoverBanner(
    state: StreamFailoverUiState,
    modifier: Modifier = Modifier
) {
    val visible = state.isRecovering || state.showRestored
    val message = state.message ?: return

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
        }
    }
}
