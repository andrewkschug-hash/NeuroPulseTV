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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

val ProfileAvatarColors = listOf(
    Color(0xFF1C3A6B),
    Color(0xFF1A3D2B),
    Color(0xFF3D1A1A),
    Color(0xFF2A1A3D)
)

@Composable
fun ProfilePickerScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var focusedIndex by remember { mutableIntStateOf(0) }
    var showPinFor by remember { mutableStateOf<UserProfile?>(null) }
    var showAddProfile by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    val exitAlpha = remember { Animatable(1f) }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(-20f) }
    val cardCount = profiles.size + 1
    val cardAlphas = remember(cardCount) { List(cardCount) { Animatable(0f) } }
    val cardOffsets = remember(cardCount) { List(cardCount) { Animatable(30f) } }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(cardCount) {
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
        focusRequester.requestFocus()
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
        GridWordmark(
            modifier = Modifier.padding(start = 24.dp, top = 20.dp),
            fontSize = 28.sp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Who's watching?",
                color = TextPrimary.copy(alpha = titleAlpha.value),
                fontFamily = DmSansFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.offset(y = titleOffset.value.dp)
            )
            Text(
                text = "Select your profile",
                color = TextSecondary.copy(alpha = titleAlpha.value),
                fontFamily = DmSansFamily,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .offset(y = titleOffset.value.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                                true
                            }
                            Key.DirectionRight -> {
                                focusedIndex = (focusedIndex + 1).coerceAtMost(cardCount - 1)
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                if (focusedIndex < profiles.size) {
                                    selectProfile(profiles[focusedIndex])
                                } else {
                                    showAddProfile = true
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                profiles.forEachIndexed { index, profile ->
                    ProfileCard(
                        name = profile.name,
                        initials = profileInitials(profile.name),
                        avatarColor = parseAvatarColor(profile.avatarColor, index),
                        focused = focusedIndex == index,
                        alpha = cardAlphas.getOrNull(index)?.value ?: 1f,
                        offsetY = cardOffsets.getOrNull(index)?.value ?: 0f,
                        onClick = { selectProfile(profile) }
                    )
                }
                AddProfileCard(
                    focused = focusedIndex == profiles.size,
                    alpha = cardAlphas.getOrNull(profiles.size)?.value ?: 1f,
                    offsetY = cardOffsets.getOrNull(profiles.size)?.value ?: 0f,
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

    Column(
        modifier = Modifier
            .width(140.dp)
            .height(170.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = offsetY + translateY
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(140.dp, 130.dp),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
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
        }
        Text(
            text = name,
            color = nameColor,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun AddProfileCard(
    focused: Boolean,
    alpha: Float,
    offsetY: Float,
    onClick: () -> Unit
) {
    val scale = if (focused) 1.12f else 1f
    val translateY = if (focused) -6f else 0f

    Column(
        modifier = Modifier
            .width(140.dp)
            .height(170.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = offsetY + translateY
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(onClick = onClick, modifier = Modifier.size(140.dp, 130.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF3B3B50), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = TextSecondary,
                        fontSize = 32.sp
                    )
                }
            }
        }
        Text(
            text = "Add Profile",
            color = TextSecondary,
            fontFamily = DmSansFamily,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
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
    var shakeOffset by remember { Animatable(0f) }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(Color(0xFF1A1A28))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Create Profile",
                color = TextPrimary,
                fontFamily = DmSansFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Profile name", fontFamily = DmSansFamily) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm) { Text("Create", fontFamily = DmSansFamily) }
                Button(onClick = onDismiss) { Text("Cancel", fontFamily = DmSansFamily) }
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
