package com.grid.tv.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val NavIconGraphicSize = 20.dp

@Composable
fun GridNavTabIcon(
    tab: EpgNavTab,
    tint: Color,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(NavIconGraphicSize)) {
        val w = size.width
        val h = size.height
        val strokeWidth = (1.75).dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (tab) {
            EpgNavTab.Search -> drawSearchIcon(tint, w, h, stroke, strokeWidth)
            EpgNavTab.Guide, EpgNavTab.Home -> drawMenuIcon(tint, w, h, strokeWidth)
            EpgNavTab.Vod, EpgNavTab.Movies, EpgNavTab.Series -> drawGridIcon(tint, w, h, stroke)
            EpgNavTab.Favorites -> drawStarIcon(tint, w, h, stroke)
            EpgNavTab.Recordings -> drawRecordingsIcon(tint, w, h, stroke, selected)
            EpgNavTab.Settings -> drawSettingsIcon(tint, w, h, stroke)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSearchIcon(
    tint: Color,
    w: Float,
    h: Float,
    stroke: Stroke,
    strokeWidth: Float
) {
    val radius = w * 0.28f
    val center = Offset(w * 0.44f, h * 0.44f)
    drawCircle(color = tint, radius = radius, center = center, style = stroke)
    val handleStart = Offset(
        center.x + radius * 0.62f,
        center.y + radius * 0.62f
    )
    drawLine(
        color = tint,
        start = handleStart,
        end = Offset(w * 0.86f, h * 0.86f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMenuIcon(
    tint: Color,
    w: Float,
    h: Float,
    strokeWidth: Float
) {
    val inset = w * 0.16f
    listOf(0.34f, 0.5f, 0.66f).forEach { yFraction ->
        drawLine(
            color = tint,
            start = Offset(inset, h * yFraction),
            end = Offset(w - inset, h * yFraction),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridIcon(
    tint: Color,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val gap = w * 0.1f
    val cell = (w - gap * 2f) / 3f
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(1.5f, 1.5f),
                style = stroke
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarIcon(
    tint: Color,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val path = starPath(Offset(w / 2f, h / 2f), w * 0.42f, w * 0.17f)
    drawPath(path = path, color = tint, style = stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRecordingsIcon(
    tint: Color,
    w: Float,
    h: Float,
    stroke: Stroke,
    selected: Boolean
) {
    val radius = w * 0.22f
    val center = Offset(w / 2f, h / 2f)
    if (selected) {
        drawCircle(color = Color(0xFFFF5252), radius = radius, center = center)
    } else {
        drawCircle(color = tint, radius = radius, center = center, style = stroke)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSettingsIcon(
    tint: Color,
    w: Float,
    h: Float,
    stroke: Stroke
) {
    val center = Offset(w / 2f, h / 2f)
    drawCircle(color = tint, radius = w * 0.18f, center = center, style = stroke)
    val outer = w * 0.34f
    for (i in 0 until 6) {
        val angle = Math.toRadians((i * 60.0) - 90.0)
        val inner = Offset(
            center.x + cos(angle).toFloat() * outer * 0.55f,
            center.y + sin(angle).toFloat() * outer * 0.55f
        )
        val outerPt = Offset(
            center.x + cos(angle).toFloat() * outer,
            center.y + sin(angle).toFloat() * outer
        )
        drawLine(
            color = tint,
            start = inner,
            end = outerPt,
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

private fun starPath(center: Offset, outerRadius: Float, innerRadius: Float): Path {
    return Path().apply {
        for (i in 0 until 10) {
            val angle = Math.PI / 2 + i * Math.PI / 5
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val x = center.x + cos(angle).toFloat() * radius
            val y = center.y - sin(angle).toFloat() * radius
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
