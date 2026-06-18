package com.grid.tv.ui.screen.onboarding

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import com.grid.tv.ui.component.GridPrimaryButton
import com.grid.tv.ui.component.TvFocusChain
import com.grid.tv.ui.component.TvTextField
import com.grid.tv.ui.component.tvFocusChainNavigation
import com.grid.tv.ui.component.tvFocusScrollIntoView
import com.grid.tv.ui.theme.DmSansFamily
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal val OnboardingBg = Color(0xFF0A0A0F)
internal val OnboardingTextPrimary = Color(0xFFF2F2F5)
internal val OnboardingTextSecondary = Color(0xFF8888A0)
internal val OnboardingTextMuted = Color(0xFF9CA3AF)
internal val OnboardingAccent = Color(0xFF3B82F6)
internal val OnboardingCardBg = Color(0xFF13131A)
internal val OnboardingInputBg = Color(0xFF1E1E2E)
internal val OnboardingError = Color(0xFFFF4444)
internal val OnboardingBorderSubtle = Color(0x14FFFFFF)
internal val OnboardingSelectedBg = Color(0x143B82F6)
internal val MethodCardWidth = 540.dp
private val IconBoxSize = 44.dp

@Composable
internal fun MethodCard(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val isActive = focused
    val bg = if (isActive) OnboardingSelectedBg else OnboardingCardBg
    val borderColor = if (isActive) OnboardingAccent else OnboardingBorderSubtle
    val borderWidth = if (isActive) 2.dp else 1.dp
    val chevronRotation by animateFloatAsState(
        targetValue = if (isActive) 90f else 0f,
        animationSpec = tween(200),
        label = "chevronRotate"
    )

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .width(MethodCardWidth)
            .height(84.dp)
            .tvFocusScrollIntoView()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .semantics { contentDescription = "$title, $subtitle" },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
        ) {
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 14.dp)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(IconBoxSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 20.sp, color = iconColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = OnboardingTextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = OnboardingTextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
                Text(
                    text = "›",
                    color = if (isActive) OnboardingTextPrimary else OnboardingTextMuted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
        }
    }
}

@Composable
internal fun OnboardingErrorBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x26FF4444))
            .border(1.dp, OnboardingError.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "!", color = OnboardingError, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = message,
            color = OnboardingError,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
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
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = -1,
    chain: TvFocusChain? = null,
    onEditingChanged: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
    onImeDone: (() -> Unit)? = null
) {
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = label,
        keyboardType = keyboardType,
        isPassword = isPassword,
        error = error,
        modifier = modifier,
        focusRequester = focusRequester,
        chainIndex = chainIndex,
        chain = chain,
        onEditingChanged = onEditingChanged,
        onNavigateBack = onNavigateBack,
        imeAction = imeAction,
        onImeNext = onImeNext,
        onImeDone = onImeDone
    )
}

@Composable
internal fun ConnectButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = -1,
    chain: TvFocusChain? = null,
    onNavigateBack: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxWidth()) {
        GridPrimaryButton(
            text = if (loading) "Connecting…" else "Connect",
            onClick = onClick,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .tvFocusScrollIntoView()
                .onFocusChanged { if (it.isFocused) chain?.onItemFocused(chainIndex) }
                .then(
                    if (chain != null && chainIndex >= 0) {
                        Modifier.tvFocusChainNavigation(chain, chainIndex, onNavigateBack)
                    } else {
                        Modifier
                    }
                ),
            contentDescription = if (loading) "Connecting" else "Connect"
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * Ghost "Set up later" link for the IPTV method-picker screen.
 * Skips onboarding and navigates to home. Must stay on that screen — do not remove.
 */
@Composable
internal fun OnboardingSkipLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chain: TvFocusChain? = null,
    chainIndex: Int = -1
) {
    var focused by remember { mutableStateOf(false) }
    val textColor = if (focused) OnboardingTextPrimary else OnboardingTextMuted

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .tvFocusScrollIntoView()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) chain?.onItemFocused(chainIndex)
            }
            .then(
                if (chain != null && chainIndex >= 0) {
                    Modifier.tvFocusChainNavigation(chain, chainIndex, onClick)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = "Set up IPTV later" },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        )
    ) {
        Text(
            text = "Set up later",
            color = textColor,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
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

