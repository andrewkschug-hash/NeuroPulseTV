package com.neuropulse.tv.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import com.neuropulse.tv.domain.model.UserProfile
import com.neuropulse.tv.ui.component.GridWordmark
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0A0A0F)
private val TextPrimary = Color(0xFFF2F2F5)
private val TextSecondary = Color(0xFF8888A0)
private val Accent = Color(0xFF3B8FFF)
private val InputBg = Color(0xFF13131A)
private val BorderSubtle = Color(0x1FFFFFFF)

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

    var focusedProfileIndex by remember { mutableIntStateOf(0) }
    var focusedOnAddLink by remember { mutableStateOf(false) }
    var showPinFor by remember { mutableStateOf<UserProfile?>(null) }
    var showAddProfile by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    val exitAlpha = remember { Animatable(1f) }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(-20f) }
    val cardCount = if (hasProfiles) profiles.size else 1
    val cardAlphas = remember(cardCount) { List(cardCount) { Animatable(0f) } }
    val cardOffsets = remember(cardCount) { List(cardCount) { Animatable(30f) } }
    val rowFocusRequester = remember { FocusRequester() }
    val addLinkFocusRequester = remember { FocusRequester() }

    LaunchedEffect(cardCount, hasProfiles) {
        focusedProfileIndex = 0
        focusedOnAddLink = false
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
        rowFocusRequester.requestFocus()
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            GridWordmark(fontSize = 28.sp)

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Who's watching?",
                color = TextPrimary.copy(alpha = titleAlpha.value),
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
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
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .offset(y = titleOffset.value.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!hasProfiles) {
                AddProfileCard(
                    label = "Add a Profile",
                    focused = true,
                    alpha = cardAlphas.firstOrNull()?.value ?: 1f,
                    offsetY = cardOffsets.firstOrNull()?.value ?: 0f,
                    onClick = { showAddProfile = true },
                    modifier = Modifier
                        .focusRequester(rowFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter)
                            ) {
                                showAddProfile = true
                                true
                            } else false
                        }
                )
            } else {
                Row(
                    modifier = Modifier
                        .focusRequester(rowFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (focusedOnAddLink) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    focusedProfileIndex = (focusedProfileIndex - 1).coerceAtLeast(0)
                                    true
                                }
                                Key.DirectionRight -> {
                                    focusedProfileIndex = (focusedProfileIndex + 1).coerceAtMost(profiles.lastIndex)
                                    true
                                }
                                Key.DirectionDown -> {
                                    focusedOnAddLink = true
                                    addLinkFocusRequester.requestFocus()
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    selectProfile(profiles[focusedProfileIndex])
                                    true
                                }
                                else -> false
                            }
                        }
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    profiles.forEachIndexed { index, profile ->
                        ProfileCard(
                            name = profile.name,
                            initials = profileInitials(profile.name),
                            avatarColor = parseAvatarColor(profile.avatarColor, index),
                            focused = !focusedOnAddLink && focusedProfileIndex == index,
                            alpha = cardAlphas.getOrNull(index)?.value ?: 1f,
                            offsetY = cardOffsets.getOrNull(index)?.value ?: 0f,
                            onClick = { selectProfile(profile) }
                        )
                    }
                }

                AddAnotherProfileLink(
                    focused = focusedOnAddLink,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .focusRequester(addLinkFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp -> {
                                    focusedOnAddLink = false
                                    rowFocusRequester.requestFocus()
                                    true
                                }
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    showAddProfile = true
                                    true
                                }
                                else -> false
                            }
                        },
                    onClick = { showAddProfile = true }
                )
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
private fun ProfileCard(
    name: String,
    initials: String,
    avatarColor: Color,
    focused: Boolean,
    alpha: Float,
    offsetY: Float,
    onClick: () -> Unit
) {
    val scale = if (focused) 1.12f else 1f
    val nameColor = if (focused) TextPrimary else TextPrimary.copy(alpha = 0.85f)
    val translateY = if (focused) -6f else 0f

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(140.dp)
            .height(170.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = offsetY + translateY
            },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(avatarColor)
                        .then(
                            if (focused) Modifier.border(2.5.dp, Accent, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = nameColor,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddProfileCard(
    label: String,
    focused: Boolean,
    alpha: Float,
    offsetY: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = if (focused) 1.12f else 1f
    val translateY = if (focused) -6f else 0f
    val nameColor = if (focused) TextPrimary else TextSecondary

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .height(170.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = offsetY + translateY
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .border(
                            width = if (focused) 2.5.dp else 2.dp,
                            color = if (focused) Accent else Color(0xFF3B3B50),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = if (focused) TextPrimary else TextSecondary,
                        fontSize = 32.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = nameColor,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddAnotherProfileLink(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (focused) TextPrimary else TextSecondary
    Surface(onClick = onClick, modifier = modifier) {
        Text(
            text = "+ Add another profile",
            color = textColor,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            textDecoration = if (focused) TextDecoration.Underline else TextDecoration.None,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
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
    var fieldFocused by remember { mutableStateOf(false) }
    var createFocused by remember { mutableStateOf(false) }
    var cancelFocused by remember { mutableStateOf(false) }
    val fieldFocusRequester = remember { FocusRequester() }
    val createFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val initials = profileInitials(name.ifBlank { "?" })

    LaunchedEffect(Unit) { fieldFocusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
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
                .width(440.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF14141E))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ProfileAvatarColors.first()),
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Create Profile",
                    color = TextPrimary,
                    fontFamily = DmSansFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Add a name for this household member",
                    color = TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Profile name",
                    color = TextSecondary,
                    fontFamily = DmSansFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = DmSansFamily,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .focusRequester(fieldFocusRequester)
                        .focusable()
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .drawBehind {
                            val stroke = if (fieldFocused) 2.dp.toPx() else 1.dp.toPx()
                            val color = if (fieldFocused) Accent else BorderSubtle
                            drawRect(color = InputBg, size = size)
                            drawRect(
                                color = color,
                                size = size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .focusRequester(cancelFocusRequester)
                        .onFocusChanged { cancelFocused = it.isFocused }
                ) {
                    Text(
                        text = "Cancel",
                        color = if (cancelFocused) TextPrimary else TextSecondary,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    onClick = onConfirm,
                    modifier = Modifier
                        .focusRequester(createFocusRequester)
                        .onFocusChanged { createFocused = it.isFocused }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (createFocused) Color(0xFF5AA3FF) else Accent)
                ) {
                    Text(
                        text = "Create",
                        color = Color.White,
                        fontFamily = DmSansFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

private fun profileInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.isNotEmpty() -> parts[0].take(2).uppercase()
        else -> "?"
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
