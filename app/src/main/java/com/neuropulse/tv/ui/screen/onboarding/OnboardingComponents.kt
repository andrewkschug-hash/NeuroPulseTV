package com.neuropulse.tv.ui.screen.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.neuropulse.tv.ui.theme.DmSansFamily

internal val OnboardingBg = Color(0xFF0A0A0F)
internal val OnboardingTextPrimary = Color(0xFFF2F2F5)
internal val OnboardingTextSecondary = Color(0xFF8888A0)
internal val OnboardingTextMuted = Color(0xFF555568)
internal val OnboardingAccent = Color(0xFF3B8FFF)
internal val OnboardingCardBg = Color(0xFF13131A)
internal val OnboardingCardFocusBg = Color(0xFF1C1C2E)
internal val OnboardingInputBg = Color(0xFF13131A)
internal val OnboardingError = Color(0xFFFF4444)
internal val OnboardingBorderSubtle = Color(0x14FFFFFF)

@Composable
internal fun MethodCard(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "methodCardScale")
    val bg = if (focused) OnboardingCardFocusBg else OnboardingCardBg
    val borderColor = if (focused) OnboardingAccent else OnboardingBorderSubtle
    val borderWidth = if (focused) 1.5.dp else 1.dp
    val arrowColor = if (focused) OnboardingTextPrimary else OnboardingTextMuted

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                        .background(Color(0xFF1C3A6B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        color = OnboardingAccent,
                        fontFamily = DmSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = icon, fontSize = 24.sp, color = iconColor, modifier = Modifier.width(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = OnboardingTextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        color = OnboardingTextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp
                    )
                }
                Text(text = ">", color = arrowColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun OnboardingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (value.isNotBlank() || focused) {
            Text(
                text = label,
                color = OnboardingTextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = OnboardingTextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (isPassword && !showPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                cursorBrush = SolidColor(OnboardingAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .drawBehind {
                        drawRect(color = OnboardingInputBg, size = size)
                        val stroke = if (focused) 1.5.dp.toPx() else 1.dp.toPx()
                        val color = when {
                            error != null -> OnboardingError
                            focused -> OnboardingAccent
                            else -> Color.White.copy(alpha = 0.10f)
                        }
                        drawRect(color = color, size = size, style = Stroke(width = stroke))
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && !focused) {
                            Text(
                                text = if (focused || value.isNotBlank()) label else placeholder,
                                color = OnboardingTextMuted,
                                fontFamily = DmSansFamily,
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                }
            )
            if (isPassword) {
                Surface(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = if (showPassword) "Hide" else "Show",
                        color = OnboardingTextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = OnboardingError,
                fontFamily = DmSansFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
internal fun ConnectButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "connectBtnScale")
    val bg = if (focused) Color(0xFF5AA3FF) else OnboardingAccent

    Surface(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Connecting…",
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "Connect",
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun AnimatedCheckmark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .drawBehind {
                drawCircle(color = OnboardingAccent.copy(alpha = 0.15f), radius = size.minDimension / 2f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.34f)
                }
                drawPath(
                    path = path,
                    color = OnboardingAccent,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
    )
}

@Composable
internal fun IptvInfoOverlay(onDismiss: () -> Unit) {
    val dismissFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        dismissFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .background(Color(0xFF1A1A28), RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What is IPTV?",
                color = OnboardingTextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "IPTV delivers live TV channels over the internet instead of cable or satellite. " +
                    "Your provider gives you login details — a server URL, M3U link, or portal address — " +
                    "which GRID uses to load your channel list.",
                color = OnboardingTextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Text(
                text = "Contact your IPTV provider if you don't have credentials yet. " +
                    "They typically offer Xtream Codes, an M3U URL, or a Stalker portal with a MAC address.",
                color = OnboardingTextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .focusRequester(dismissFocusRequester)
            ) {
                Text(
                    text = "Got it",
                    color = OnboardingTextPrimary,
                    fontFamily = DmSansFamily,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
