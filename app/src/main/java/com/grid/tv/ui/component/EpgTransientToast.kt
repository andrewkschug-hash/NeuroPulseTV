package com.grid.tv.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors

/** Brief, non-focusable confirmation banner for EPG actions (auto-cleared by the host). */
@Composable
fun EpgTransientToast(
    message: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
) {
    if (message.isNullOrBlank()) return
    Box(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        Text(
            text = message,
            color = EpgColors.TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier
                .background(EpgColors.DetailPanelBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
