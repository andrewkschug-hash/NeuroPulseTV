package com.neuropulse.tv.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.ui.component.GridBrandWordmark
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SplashBase = Color(0xFF040408)
private val SplashMid = Color(0xFF08081A)
private val Accent = Color(0xFF3B8FFF)

private const val REVEAL_MS = 900L
private const val HOLD_MS = 1_600L
private const val EXIT_MS = 550L

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var revealStarted by remember { mutableStateOf(false) }
    var exitStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        revealStarted = true
        delay(REVEAL_MS + HOLD_MS)
        exitStarted = true
        delay(EXIT_MS)
        onFinished()
    }

    val sceneAlpha by animateFloatAsState(
        targetValue = if (exitStarted) 0f else 1f,
        animationSpec = tween(EXIT_MS.toInt(), easing = FastOutSlowInEasing),
        label = "sceneAlpha"
    )

    val backdropAlpha by animateFloatAsState(
        targetValue = if (revealStarted) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "backdropAlpha"
    )

    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (revealStarted) 1f else 0f,
        animationSpec = tween(800, delayMillis = 180, easing = FastOutSlowInEasing),
        label = "wordmarkAlpha"
    )

    val wordmarkScale by animateFloatAsState(
        targetValue = if (revealStarted) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f
        ),
        label = "wordmarkScale"
    )

    val dividerProgress by animateFloatAsState(
        targetValue = if (revealStarted) 1f else 0f,
        animationSpec = tween(650, delayMillis = 520, easing = FastOutSlowInEasing),
        label = "dividerProgress"
    )

    val infinite = rememberInfiniteTransition(label = "splashAmbient")
    val bloomPulse by infinite.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bloomPulse"
    )
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing)
        ),
        label = "ringRotation"
    )
    val shimmerOffset by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val glowAlpha = if (revealStarted) 0.35f + bloomPulse * 0.35f else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = sceneAlpha }
            .background(
                Brush.verticalGradient(
                    colors = listOf(SplashMid, SplashBase, Color.Black)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdropAlpha }
        ) {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.42f
            val vanishX = w * 0.5f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Accent.copy(alpha = 0.14f),
                        Accent.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(vanishX, horizonY),
                    radius = w * 0.55f
                )
            )

            val floorLines = 18
            for (i in 0..floorLines) {
                val t = i / floorLines.toFloat()
                val y = horizonY + (h - horizonY) * t
                val spread = 0.15f + t * 0.85f
                val leftX = vanishX - w * spread * 0.5f
                val rightX = vanishX + w * spread * 0.5f
                drawLine(
                    color = Accent.copy(alpha = 0.04f + t * 0.08f),
                    start = Offset(leftX, y),
                    end = Offset(rightX, y),
                    strokeWidth = 1f
                )
            }

            val radialLines = 24
            for (i in 0 until radialLines) {
                val angle = (i / radialLines.toFloat()) * PI.toFloat() * 2f
                val endX = vanishX + cos(angle) * w * 0.75f
                val endY = horizonY + sin(angle).coerceAtLeast(0f) * h * 0.6f
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(vanishX, horizonY),
                    end = Offset(endX, endY),
                    strokeWidth = 1f
                )
            }

            val ringCount = 4
            for (ring in 1..ringCount) {
                val radius = w * 0.08f * ring
                drawCircle(
                    color = Accent.copy(alpha = 0.035f / ring),
                    radius = radius,
                    center = Offset(vanishX, horizonY),
                    style = Stroke(width = 1f)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(420.dp)
                .graphicsLayer { alpha = backdropAlpha * bloomPulse },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Accent.copy(alpha = 0.35f),
                                Accent.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Canvas(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    alpha = backdropAlpha * 0.5f
                    rotationZ = ringRotation
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val path = Path().apply {
                moveTo(cx, cy - size.height * 0.38f)
                lineTo(cx + size.width * 0.38f, cy)
                lineTo(cx, cy + size.height * 0.38f)
                lineTo(cx - size.width * 0.38f, cy)
                close()
            }
            drawPath(
                path = path,
                color = Accent.copy(alpha = 0.12f),
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Accent.copy(alpha = 0.08f),
                radius = size.minDimension * 0.32f,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                GridBrandWordmark(
                    fontSize = 76.sp,
                    letterSpacing = 18.sp,
                    alpha = wordmarkAlpha,
                    scale = wordmarkScale,
                    glowAlpha = glowAlpha,
                    dividerWidth = 220.dp,
                    dividerProgress = dividerProgress,
                    dividerAlpha = wordmarkAlpha
                )

                if (revealStarted && !exitStarted) {
                    Box(
                        modifier = Modifier
                            .size(width = 280.dp, height = 90.dp)
                            .graphicsLayer {
                                alpha = 0.35f
                                translationX = shimmerOffset * 140.dp.toPx()
                            }
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.18f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            if (revealStarted) {
                SplashLoadingDots(
                    visible = !exitStarted,
                    modifier = Modifier.graphicsLayer { alpha = wordmarkAlpha * 0.85f }
                )
            }
        }

        Text(
            text = "Your guide. Your grid.",
            color = EpgColors.TextDimmed.copy(alpha = 0.55f * wordmarkAlpha),
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .graphicsLayer { alpha = wordmarkAlpha }
        )
    }
}

@Composable
private fun SplashLoadingDots(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val infinite = rememberInfiniteTransition(label = "loadingDots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val dotAlpha by infinite.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, delayMillis = index * 180, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha$index"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(Accent.copy(alpha = 0.9f), CircleShape)
            )
        }
    }
}
