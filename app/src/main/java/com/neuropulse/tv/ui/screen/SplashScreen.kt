package com.neuropulse.tv.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuropulse.tv.ui.theme.BarlowCondensedFamily
import kotlinx.coroutines.delay

private val SplashBg = Color(0xFF080810)
private val BloomBlue = Color(0xFF2563EB)
private val LetterWhite = Color.White

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var bloomVisible by remember { mutableStateOf(false) }
    var lettersVisible by remember { mutableStateOf(false) }
    var underlineVisible by remember { mutableStateOf(false) }
    var logoFadingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bloomVisible = true
        delay(300)
        lettersVisible = true
        delay(400)
        underlineVisible = true
        delay(200)
        delay(600)
        logoFadingOut = true
        delay(100)
        onFinished()
    }

    val bloomAlpha by animateFloatAsState(
        targetValue = if (bloomVisible) 0.35f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "bloomAlpha"
    )

    val gridAlpha by animateFloatAsState(
        targetValue = if (bloomVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "gridAlpha"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (logoFadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = gridAlpha * 0.10f }
        ) {
            val lineColor = Color.White.copy(alpha = 0.4f)
            val lineCount = 14
            for (i in 0..lineCount) {
                val y = size.height * i / lineCount
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (i in 0..lineCount) {
                val x = size.width * i / lineCount
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
        }

        Box(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer { alpha = logoAlpha },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer { alpha = bloomAlpha }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BloomBlue.copy(alpha = 1f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SplashLetter("G", index = 0, lettersVisible = lettersVisible)
                    SplashLetter("R", index = 1, lettersVisible = lettersVisible)
                    SplashLetter("I", index = 2, lettersVisible = lettersVisible)
                    SplashLetter("D", index = 3, lettersVisible = lettersVisible)
                }

                SplashUnderline(
                    visible = underlineVisible,
                    modifier = Modifier
                        .width(220.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SplashLetter(
    letter: String,
    index: Int,
    lettersVisible: Boolean
) {
    val delayPerLetter = 100
    var letterReady by remember { mutableStateOf(false) }

    LaunchedEffect(lettersVisible) {
        if (lettersVisible) {
            delay(index * delayPerLetter.toLong())
            letterReady = true
        } else {
            letterReady = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (letterReady) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "letterAlpha$index"
    )
    val scale by animateFloatAsState(
        targetValue = if (letterReady) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
            visibilityThreshold = 0.001f
        ),
        label = "letterScale$index"
    )

    Text(
        text = letter,
        fontFamily = BarlowCondensedFamily,
        fontSize = 72.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 8.sp,
        color = LetterWhite.copy(alpha = alpha),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
private fun SplashUnderline(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val underlineWidth by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = 50,
            easing = FastOutSlowInEasing
        ),
        label = "underlineWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(underlineWidth)
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(BloomBlue)
    )
}
