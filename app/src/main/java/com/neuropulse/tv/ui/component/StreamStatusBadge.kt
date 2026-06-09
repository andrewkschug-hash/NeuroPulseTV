package com.neuropulse.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neuropulse.tv.player.StreamPlaybackStatus
import com.neuropulse.tv.player.badgeColor
import com.neuropulse.tv.player.userLabel
import com.neuropulse.tv.ui.theme.DmSansFamily

@Composable
fun StreamStatusBadge(
    status: StreamPlaybackStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val label = status.userLabel()
    if (label.isBlank()) return

    val color = status.badgeColor()
    if (compact) {
        Row(
            modifier = modifier
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    } else {
        Row(
            modifier = modifier
                .background(color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
