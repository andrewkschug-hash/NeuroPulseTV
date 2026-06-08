package com.neuropulse.tv.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.ui.theme.BarlowCondensedFamily

@Composable
fun GridWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    color: Color = Color(0xFFF2F2F5),
    letterSpacing: TextUnit = fontSize * 0.25f
) {
    Text(
        text = "GRID",
        modifier = modifier,
        color = color,
        fontFamily = BarlowCondensedFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = letterSpacing
    )
}
