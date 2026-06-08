package com.neuropulse.tv.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.ui.theme.BarlowCondensedFamily
import com.neuropulse.tv.ui.theme.DmSansFamily
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val SplashBg = Color(0xFF0A0A0F)
private val TextPrimary = Color(0xFFF2F2F5)
private val TextSecondary = Color(0xFF8888A0)
private val Accent = Color(0xFF3B8FFF)
private const val WordmarkWidthDp = 220f

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val gridProgress = remember { Animatable(0f) }
    val letterAlphas = remember { List(4) { Animatable(0f) } }
    val letterOffsets = remember { List(4) { Animatable(24f) } }
    val accentWidth = remember { Animatable(0f) }
    val taglineAlpha = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }
    val gridAlpha = remember { Animatable(1f) }
    val noisePoints = remember { generateNoisePoints(2400) }

    LaunchedEffect(Unit) {
        launch { gridProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
        delay(300)
        coroutineScope {
            letterAlphas.forEachIndexed { index, anim ->
                launch {
                    delay(index * 60L)
                    launch { anim.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
                    letterOffsets[index].animateTo(0f, tween(360, easing = FastOutSlowInEasing))
                }
            }
        }
        delay(600)
        accentWidth.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        taglineAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        delay(800)
        coroutineScope {
            launch { exitScale.animateTo(1.08f, tween(600, easing = FastOutSlowInEasing)) }
            launch { exitAlpha.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
            launch {
                delay(200)
                gridAlpha.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
            }
        }
        delay(600)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = gridAlpha.value }
        ) {
            val progress = gridProgress.value
            val lineColor = Color.White.copy(alpha = 0.04f)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val hExtent = size.width / 2f * progress
            val vExtent = size.height / 2f * progress
            val lineCount = 14
            for (i in 0..lineCount) {
                val y = size.height * i / lineCount
                drawLine(lineColor, Offset(cx - hExtent, y), Offset(cx + hExtent, y), strokeWidth = 1f)
            }
            for (i in 0..lineCount) {
                val x = size.width * i / lineCount
                drawLine(lineColor, Offset(x, cy - vExtent), Offset(x, cy + vExtent), strokeWidth = 1f)
            }
            noisePoints.forEach { (nx, ny, alpha) ->
                drawCircle(
                    color = Color.White.copy(alpha = alpha * gridAlpha.value),
                    radius = 1f,
                    center = Offset(nx * size.width, ny * size.height)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
        ) {
            Row {
                "GRID".forEachIndexed { index, char ->
                    Text(
                        text = char.toString(),
                        color = TextPrimary.copy(alpha = letterAlphas[index].value),
                        fontFamily = BarlowCondensedFamily,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 24.sp,
                        modifier = Modifier.offset(y = letterOffsets[index].value.dp)
                    )
                }
            }
            val lineFraction = 0.2f + 0.8f * accentWidth.value
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width((WordmarkWidthDp * lineFraction).dp)
                    .height(2.dp)
                    .background(Accent)
            )
            Text(
                text = "LIVE TV GUIDE",
                color = TextSecondary.copy(alpha = taglineAlpha.value),
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                letterSpacing = 5.6.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private data class NoisePoint(val x: Float, val y: Float, val alpha: Float)

private fun generateNoisePoints(count: Int): List<NoisePoint> {
    val random = Random(42)
    return List(count) {
        NoisePoint(
            x = random.nextFloat(),
            y = random.nextFloat(),
            alpha = random.nextFloat() * 0.02f + 0.03f
        )
    }
}
