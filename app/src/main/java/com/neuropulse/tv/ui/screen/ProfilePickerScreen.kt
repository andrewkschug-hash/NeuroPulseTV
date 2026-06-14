package com.neuropulse.tv.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.ui.component.requestFocusSafely
import com.neuropulse.tv.ui.component.requestFocusSafelyAfterLayout
import com.neuropulse.tv.ui.component.GridModal
import com.neuropulse.tv.ui.component.GridOutlinedButton
import com.neuropulse.tv.ui.component.GridPrimaryButton
import com.neuropulse.tv.ui.component.TvTextField
import com.neuropulse.tv.ui.theme.BarlowCondensedFamily
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
import com.neuropulse.tv.ui.viewmodel.ProfileViewModel
import com.neuropulse.tv.util.MAX_HOUSEHOLD_PROFILES
import com.neuropulse.tv.util.profileInitials
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0A0A0F)
private val TextPrimary = Color(0xFFF2F2F5)
private val TextSecondary = Color(0xFF6B6B80)
private val Accent = EpgColors.FocusBorder
private val CardBg = Color(0xFF1A1A2E)
private val CardBorderRest = Color(0xFF2A2A40)
private val AvatarBorderRest = Color(0xFF3B3B50)
private val CardShape = RoundedCornerShape(14.dp)
private val CardWidth = 148.dp
private val CardHeight = 200.dp

val ProfileAvatarColors = listOf(
    Color(0xFF1C3A6B),
    Color(0xFF1A3D2B),
    Color(0xFF3D1A1A),
    Color(0xFF2A1A3D),
    Color(0xFF1A4D5C),
    Color(0xFF4D3A1A),
    Color(0xFF3B8FFF),
    Color(0xFF8B3BFF)
)

@Composable
fun ProfilePickerScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hasProfiles = profiles.isNotEmpty()
    val firstProfileFocusRequester = remember { FocusRequester() }

    var showPinFor by remember { mutableStateOf<UserProfile?>(null) }
    var showAddProfile by remember { mutableStateOf(false) }
    var showMaxProfilesHint by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    val exitAlpha = remember { Animatable(1f) }
    val atProfileLimit = profiles.size >= MAX_HOUSEHOLD_PROFILES

    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(-20f) }
    val cardCount = if (hasProfiles) profiles.size else 1
    val cardAlphas = remember(cardCount) { List(cardCount) { Animatable(0f) } }
    val cardOffsets = remember(cardCount) { List(cardCount) { Animatable(30f) } }
    LaunchedEffect(cardCount, hasProfiles) {
        titleAlpha.snapTo(0f)
        titleOffset.snapTo(-20f)
        cardAlphas.forEach { it.snapTo(0f) }
        cardOffsets.forEach { it.snapTo(30f) }
        titleAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        titleOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        repeat(cardCount) { i ->
            launch {
                delay(i * 80L)
                cardAlphas[i].animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                cardOffsets[i].animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            }
        }
    }

    LaunchedEffect(hasProfiles, profiles.map { it.id }, showPinFor, showAddProfile) {
        if (showPinFor != null || showAddProfile) return@LaunchedEffect
        delay(350)
        firstProfileFocusRequester.requestFocusSafely()
    }

    fun tryOpenAddProfile() {
        if (atProfileLimit) {
            showMaxProfilesHint = true
        } else {
            showAddProfile = true
        }
    }

    fun selectProfile(profile: UserProfile) {
        if (profile.hasPin) {
            showPinFor = profile
        } else {
            scope.launch {
                viewModel.switchProfile(profile.id)
                exitAlpha.animateTo(0f, tween(400))
                delay(400)
                onProfileSelected()
            }
        }
    }

    fun onPinVerified(profile: UserProfile) {
        showPinFor = null
        scope.launch {
            viewModel.switchProfile(profile.id)
            exitAlpha.animateTo(0f, tween(400))
            delay(400)
            onProfileSelected()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .graphicsLayer { alpha = exitAlpha.value }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProfilePickerWordmark(
                alpha = titleAlpha.value,
                offsetY = titleOffset.value
            )

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = "Who's watching?",
                color = TextPrimary.copy(alpha = titleAlpha.value),
                fontFamily = DmSansFamily,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = titleOffset.value.dp)
            )
            if (hasProfiles) {
                Text(
                    text = "Select your profile",
                    color = TextSecondary.copy(alpha = titleAlpha.value),
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .offset(y = titleOffset.value.dp)
                )
            }

            Spacer(modifier = Modifier.height(52.dp))

            if (!hasProfiles) {
                AddProfileCard(
                    label = "Add a Profile",
                    alpha = cardAlphas.firstOrNull()?.value ?: 1f,
                    offsetY = cardOffsets.firstOrNull()?.value ?: 0f,
                    onClick = { tryOpenAddProfile() },
                    focusRequester = firstProfileFocusRequester
                )
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    profiles.forEachIndexed { index, profile ->
                        ProfileCard(
                            name = profile.name,
                            initials = profileInitials(profile.name),
                            avatarColor = parseAvatarColor(profile.avatarColor, index),
                            alpha = cardAlphas.getOrNull(index)?.value ?: 1f,
                            offsetY = cardOffsets.getOrNull(index)?.value ?: 0f,
                            onClick = { selectProfile(profile) },
                            modifier = Modifier.padding(horizontal = 14.dp),
                            focusRequester = if (index == 0) firstProfileFocusRequester else null
                        )
                    }
                    if (!atProfileLimit) {
                        AddProfileCard(
                            label = "Add profile",
                            alpha = 1f,
                            offsetY = 0f,
                            onClick = { tryOpenAddProfile() },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }

                if (showMaxProfilesHint || atProfileLimit) {
                    Text(
                        text = "Up to $MAX_HOUSEHOLD_PROFILES profiles per household",
                        color = TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        if (showPinFor != null) {
            PinEntryOverlay(
                profile = showPinFor!!,
                onVerified = { onPinVerified(showPinFor!!) },
                onDismiss = { showPinFor = null },
                verifyPin = { pin -> viewModel.verifyPin(showPinFor!!.id, pin) }
            )
        }

        if (showAddProfile) {
            AddProfileDialog(
                name = newProfileName,
                onNameChange = { newProfileName = it },
                onConfirm = {
                    val color = ProfileAvatarColors[profiles.size % ProfileAvatarColors.size]
                    viewModel.createProfile(
                        name = newProfileName.ifBlank { "Profile ${profiles.size + 1}" },
                        color = colorToHex(color),
                        pin = null,
                        parental = false
                    )
                    newProfileName = ""
                    showAddProfile = false
                },
                onDismiss = {
                    newProfileName = ""
                    showAddProfile = false
                }
            )
        }

    }
}

@Composable
private fun ProfilePickerWordmark(
    alpha: Float,
    offsetY: Float
) {
    val accentLight = Color(0xFF7EB8FF)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY
            }
    ) {
        Text(
            text = "GRID",
            fontFamily = BarlowCondensedFamily,
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 14.sp,
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(Accent, accentLight, Accent)
                ),
                shadow = Shadow(
                    color = Accent.copy(alpha = 0.55f),
                    offset = Offset(0f, 0f),
                    blurRadius = 28f
                )
            )
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Accent.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun ProfileCard(
    name: String,
    initials: String,
    avatarColor: Color,
    alpha: Float,
    offsetY: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(200), label = "cardScale")

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(CardWidth)
            .height(CardHeight)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = offsetY
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.profileCardFocusGlow() else Modifier)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Accent else CardBorderRest,
                shape = CardShape
            ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CardShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = CardBg,
            focusedContainerColor = CardBg,
            pressedContainerColor = CardBg
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(avatarColor)
                    .border(
                        width = if (focused) 2.5.dp else 1.5.dp,
                        color = if (focused) Accent else AvatarBorderRest,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = name,
                color = TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddProfileCard(
    label: String,
    alpha: Float,
    offsetY: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(200), label = "addScale")

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(CardWidth)
            .height(CardHeight)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = offsetY
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.profileCardFocusGlow() else Modifier)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Accent else CardBorderRest,
                shape = CardShape
            ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CardShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = CardBg,
            focusedContainerColor = CardBg,
            pressedContainerColor = CardBg
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141420))
                    .border(
                        width = if (focused) 2.5.dp else 1.5.dp,
                        color = if (focused) Accent else AvatarBorderRest,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = if (focused) Accent else TextSecondary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Modifier.profileCardFocusGlow(): Modifier = drawBehind {
    val corner = 16.dp.toPx()
    drawRoundRect(
        color = Accent.copy(alpha = 0.3f),
        cornerRadius = CornerRadius(corner),
        style = Stroke(width = 8.dp.toPx())
    )
}

@Composable
private fun PinEntryOverlay(
    profile: UserProfile,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    verifyPin: suspend (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xFF1A1A28))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Enter PIN",
                color = TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = profile.name,
                color = TextSecondary,
                fontFamily = DmSansFamily,
                fontSize = 14.sp
            )
            Row(
                modifier = Modifier.offset(x = shakeOffset.value.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (i < pin.length) Accent else Color(0xFF2A2A3A))
                    )
                }
            }
            PinNumberPad(
                onDigit = { d ->
                    if (pin.length < 4) {
                        pin += d
                        if (pin.length == 4) {
                            scope.launch {
                                if (verifyPin(pin)) {
                                    onVerified()
                                } else {
                                    repeat(3) {
                                        shakeOffset.animateTo(12f, tween(50))
                                        shakeOffset.animateTo(-12f, tween(50))
                                    }
                                    shakeOffset.animateTo(0f, tween(50))
                                    pin = ""
                                }
                            }
                        }
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
            )
            Button(onClick = onDismiss) {
                Text("Cancel", fontFamily = DmSansFamily)
            }
        }
    }
}

@Composable
private fun PinNumberPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(64.dp))
                    } else {
                        Button(
                            onClick = {
                                if (key == "⌫") onBackspace() else onDigit(key)
                            },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Text(key, fontFamily = DmSansFamily, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ProfileNameDialog(
        title = "Create Profile",
        subtitle = "Add a name for this household member",
        name = name,
        onNameChange = onNameChange,
        confirmLabel = "Create",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun ProfileNameDialog(
    title: String,
    subtitle: String,
    name: String,
    onNameChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var fieldFocused by remember { mutableStateOf(false) }
    var avatarFocused by remember { mutableStateOf(false) }
    val fieldFocusRequester = remember { FocusRequester() }
    val initials = profileInitials(name.ifBlank { "?" })
    val avatarScale by animateFloatAsState(if (avatarFocused || fieldFocused) 1.05f else 1f, tween(150), label = "dlgAvatar")

    LaunchedEffect(Unit) { fieldFocusRequester.requestFocusSafelyAfterLayout() }

    GridModal(onDismiss = onDismiss, width = 440.dp) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                }
                .clip(CircleShape)
                .background(ProfileAvatarColors.first())
                .then(
                    if (fieldFocused) Modifier.border(2.dp, Accent, CircleShape)
                    else Modifier
                )
                .align(Alignment.CenterHorizontally)
                .onFocusChanged { avatarFocused = it.isFocused },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = DmSansFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        TvTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = "Enter a name",
            label = "Profile name",
            focusRequester = fieldFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            onHighlightChanged = { fieldFocused = it }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GridOutlinedButton(text = "Cancel", onClick = onDismiss)
            Spacer(modifier = Modifier.width(12.dp))
            GridPrimaryButton(text = confirmLabel, onClick = onConfirm)
        }
    }
}

private fun parseAvatarColor(hex: String, index: Int): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrElse { ProfileAvatarColors[index % ProfileAvatarColors.size] }
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}
