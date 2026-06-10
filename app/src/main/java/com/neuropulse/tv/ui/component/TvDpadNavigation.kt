package com.neuropulse.tv.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors

/** Shared accent for TV focus rings across the app. */
val TvFocusAccent = EpgColors.FocusBorder
private val TvInputBg = Color(0xFF1E1E2E)
private val TvInputBorder = Color(0xFF4B5563)
private val TvTextMuted = Color(0xFF9CA3AF)

fun Modifier.tvFocusRing(
    focused: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    width: androidx.compose.ui.unit.Dp = 2.dp
): Modifier = border(
    width = if (focused) width else 1.dp,
    color = if (focused) TvFocusAccent else Color.Transparent,
    shape = shape
)

@Stable
class TvFocusChain(
    val requesters: List<FocusRequester>
) {
    var focusedIndex: Int = 0
        private set

    val lastIndex: Int get() = requesters.lastIndex.coerceAtLeast(0)

    fun moveTo(index: Int) {
        if (requesters.isEmpty()) return
        val target = index.coerceIn(0, requesters.lastIndex)
        focusedIndex = target
        requesters[target].requestFocus()
    }

    fun move(delta: Int) = moveTo(focusedIndex + delta)

    fun onItemFocused(index: Int) {
        focusedIndex = index
    }
}

@Composable
fun rememberTvFocusChain(count: Int, startIndex: Int = 0): TvFocusChain {
    val requesters = remember(count) { List(count) { FocusRequester() } }
    val chain = remember(count) { TvFocusChain(requesters) }
    LaunchedEffect(count, startIndex) {
        if (count > 0) chain.moveTo(startIndex.coerceIn(0, count - 1))
    }
    return chain
}

fun Modifier.tvVerticalDpadNavigation(
    chain: TvFocusChain,
    onBack: () -> Unit,
    isEditing: () -> Boolean,
    onDismissEditing: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionDown -> {
            if (isEditing()) return@onPreviewKeyEvent false
            if (chain.focusedIndex < chain.lastIndex) {
                chain.move(1)
                true
            } else {
                false
            }
        }
        Key.DirectionUp -> {
            if (isEditing()) return@onPreviewKeyEvent false
            if (chain.focusedIndex > 0) {
                chain.move(-1)
                true
            } else {
                false
            }
        }
        Key.DirectionRight -> {
            if (isEditing()) return@onPreviewKeyEvent false
            // From first item (e.g. Back), Right moves to the next field.
            if (chain.focusedIndex == 0 && chain.lastIndex > 0) {
                chain.move(1)
                true
            } else {
                false
            }
        }
        Key.Back, Key.Escape -> {
            if (isEditing()) {
                onDismissEditing()
                true
            } else {
                onBack()
                true
            }
        }
        else -> false
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    error: String? = null,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = -1,
    chain: TvFocusChain? = null,
    onEditingChanged: (Boolean) -> Unit = {},
    onHighlightChanged: (Boolean) -> Unit = {}
) {
    var highlighted by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(highlighted) {
        if (highlighted && !editing) keyboard?.hide()
    }

    fun stopEditing() {
        if (editing) {
            editing = false
            onEditingChanged(false)
            keyboard?.hide()
        }
    }

    ColumnFieldLabel(label = label, value = value, showLabel = highlighted || editing || value.isNotBlank())

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusable(interactionSource = interactionSource)
                .onFocusChanged {
                    highlighted = it.isFocused
                    onHighlightChanged(it.isFocused)
                    if (it.isFocused) {
                        chain?.onItemFocused(chainIndex)
                    }
                    if (!it.isFocused) stopEditing()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (highlighted && !editing) {
                                editing = true
                                onEditingChanged(true)
                                keyboard?.show()
                                true
                            } else {
                                false
                            }
                        }
                        Key.Back, Key.Escape -> {
                            if (editing) {
                                stopEditing()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                .clip(RoundedCornerShape(8.dp))
                .tvFocusRing(focused = highlighted || editing)
                .border(
                    width = 1.dp,
                    color = when {
                        error != null -> Color(0xFFFF4444)
                        highlighted || editing -> Color.Transparent
                        else -> TvInputBorder
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = !editing,
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = DmSansFamily,
                    fontSize = 15.sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { stopEditing() }),
                visualTransformation = if (isPassword && !showPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                cursorBrush = SolidColor(TvFocusAccent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && !editing) {
                        Text(
                            text = placeholder,
                            color = TvTextMuted,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            )
        }
        if (isPassword) {
            var showFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { showPassword = !showPassword },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .onFocusChanged { showFocused = it.isFocused }
                    .tvFocusRing(focused = showFocused, shape = RoundedCornerShape(4.dp), width = 1.5.dp)
            ) {
                Text(
                    text = if (showPassword) "Hide" else "Show",
                    color = TvTextMuted,
                    fontFamily = DmSansFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ColumnFieldLabel(label: String?, value: String, showLabel: Boolean) {
    if (label != null && showLabel) {
        Text(
            text = label,
            color = TvTextMuted,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

@Composable
fun TvTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = -1,
    chain: TvFocusChain? = null
) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Color.White else TvTextMuted

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) chain?.onItemFocused(chainIndex)
            }
            .tvFocusRing(focused = focused, shape = RoundedCornerShape(6.dp), width = 1.5.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = DmSansFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TvBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = 0,
    chain: TvFocusChain? = null
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) chain?.onItemFocused(chainIndex)
            }
            .tvFocusRing(focused = focused, shape = RoundedCornerShape(6.dp), width = 1.5.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFF1E1E2E)
        )
    ) {
        Text(
            text = "← Back",
            color = if (focused) Color.White else TvTextMuted,
            fontFamily = DmSansFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
