package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.Program
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvImageSizing

private const val NoInformationAvailable = "No information available"

/**
 * Persistent info strip for the live guide. Tracks the logically focused channel/program
 * (updates on grid D-pad navigation). Full title + description wrap here; compact grid
 * rows stay single-line ellipsized.
 *
 * Not focusable — it mirrors focus, it never takes it.
 */
@Composable
fun EpgNowFocusedPanel(
    channel: Channel?,
    program: Program?,
    modifier: Modifier = Modifier,
) {
    if (channel == null) return

    val title = program?.title?.trim()?.takeIf { it.isNotBlank() } ?: NoInformationAvailable
    val description = program?.description?.trim()?.takeIf { it.isNotBlank() }
        ?: NoInformationAvailable
    val timeLine = program?.let {
        buildString {
            append(formatEpgTime(it.startTime))
            append(" – ")
            append(formatEpgTime(it.endTime))
            append("  ·  ")
            append(programDurationMinutes(it))
            append(" min")
        }
    }
    val initials = channel.name.take(2).uppercase()
    val context = LocalContext.current
    val logoSizePx = remember(context) { TvImageSizing.channelLogoPx(context, 40) }
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .background(Color(0xFF12121C))
            .border(width = 1.dp, color = EpgColors.BorderSubtle.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(max = 168.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Now Focused",
            color = EpgColors.TextDimmed,
            fontFamily = DmSansFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = channel.number.toString(),
                color = EpgColors.Accent,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = TvImageSizing.sizedRequest(
                        context = context,
                        data = channel.logoUrl,
                        widthPx = logoSizePx,
                        heightPx = logoSizePx,
                    ),
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A22)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1A22)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        color = EpgColors.TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = channel.name,
                color = EpgColors.TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = title,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            // Full wrap — no line limit (panel-only; grid cells stay maxLines=1).
        )
        timeLine?.let { line ->
            Text(
                text = line,
                color = EpgColors.TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
            )
        }
        Text(
            text = description,
            color = if (description == NoInformationAvailable) {
                EpgColors.TextDimmed
            } else {
                EpgColors.TextSecondary
            },
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            // No maxLines — description must never clip mid-word in this panel.
        )
    }
}
