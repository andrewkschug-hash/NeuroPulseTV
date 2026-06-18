package com.grid.tv.ui.component

import androidx.compose.ui.ExperimentalComposeUiApi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvImeKeyDispatcher
import com.grid.tv.util.TvRemoteKeyboard
import com.grid.tv.util.TvTextInputSession
import com.grid.tv.util.lockFocusWhileTyping
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared accent for TV focus rings across the app. */
val TvFocusAccent = EpgColors.FocusBorder
private val TvInputBg = Color(0xFF252836)
private val TvInputBorder = Color.Transparent
private val TvInputFocusBorder = EpgColors.FocusBorder
private val TvTextMuted = Color(0xFFB8BEC8)

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
        requesters[target].requestFocusSafely()
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

fun handleTvFocusChainKey(
    event: androidx.compose.ui.input.key.KeyEvent,
    chain: TvFocusChain,
    onBack: () -> Unit,
    isEditing: () -> Boolean,
    onDismissEditing: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (TvTextInputSession.shouldStandDownForActiveInput(event)) return false
    if (isEditing()) return false
    return when (event.key) {
        Key.DirectionDown -> {
            when (chain.focusedIndex) {
                0 -> if (chain.lastIndex > 0) {
                    chain.move(1)
                    true
                } else {
                    false
                }
                in 0 until chain.lastIndex -> {
                    chain.move(1)
                    true
                }
                else -> false
            }
        }
        Key.DirectionUp -> {
            if (chain.focusedIndex > 0) {
                chain.move(-1)
                true
            } else {
                false
            }
        }
        Key.DirectionRight -> {
            if (chain.focusedIndex == 0 && chain.lastIndex > 0) {
                chain.move(1)
                true
            } else {
                false
            }
        }
        Key.DirectionLeft -> {
            if (chain.focusedIndex == 1) {
                chain.move(-1)
                true
            } else {
                false
            }
        }
        Key.Back, Key.Escape -> {
            onBack()
            true
        }
        else -> false
    }
}

fun Modifier.tvVerticalDpadNavigation(
    chain: TvFocusChain,
    onBack: () -> Unit,
    isEditing: () -> Boolean,
    onDismissEditing: () -> Unit
): Modifier = onPreviewKeyEvent { event ->
    handleTvFocusChainKey(event, chain, onBack, isEditing, onDismissEditing)
}

fun Modifier.tvFocusChainNavigation(
    chain: TvFocusChain,
    index: Int,
    onBack: () -> Unit,
    isEditing: () -> Boolean = { false },
    onDismissEditing: () -> Unit = {}
): Modifier = onPreviewKeyEvent { event ->
    if (chain.focusedIndex != index) return@onPreviewKeyEvent false
    handleTvFocusChainKey(event, chain, onBack, isEditing, onDismissEditing)
}

internal fun isTextFieldActivateKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> true
        else -> {
            val code = event.nativeKeyEvent.keyCode
            code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                code == android.view.KeyEvent.KEYCODE_ENTER ||
                code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
        }
    }
}

internal suspend fun showTextFieldKeyboard(
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    view: android.view.View,
    focusRequester: FocusRequester? = null
) {
    focusRequester?.requestFocus()
    delay(50)
    TvRemoteKeyboard.requestIme(view)
    keyboard?.show()
}

@OptIn(ExperimentalComposeUiApi::class)
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
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onEditingChanged: (Boolean) -> Unit = {},
    onHighlightChanged: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var inputActive by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val innerFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: innerFocusRequester
    val showFocusBorder = isFocused || inputActive

    fun activateInput() {
        if (inputActive) {
            scope.launch { showTextFieldKeyboard(keyboard, view, effectiveFocusRequester) }
            return
        }
        inputActive = true
        onEditingChanged(true)
        TvTextInputSession.begin()
        scope.launch { showTextFieldKeyboard(keyboard, view, effectiveFocusRequester) }
    }

    fun dismissInput() {
        if (inputActive) {
            inputActive = false
            onEditingChanged(false)
            TvTextInputSession.end()
            keyboard?.hide()
            TvRemoteKeyboard.dismissKeyboard(view)
        }
    }

    DisposableEffect(Unit) {
        onDispose { dismissInput() }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused) dismissInput()
    }

    TvTextInputActivationEffect(active = isFocused || inputActive, onActivate = ::activateInput)

    fun handleFieldKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (inputActive) {
            return when {
                event.key == Key.Back || event.key == Key.Escape -> {
                    dismissInput()
                    true
                }
                TvImeKeyDispatcher.isImeNavigationKey(event.key) -> false
                else -> false
            }
        }
        return when {
            isTextFieldActivateKey(event) -> {
                activateInput()
                true
            }
            else -> false
        }
    }

    ColumnFieldLabel(label = label, value = value, showLabel = showFocusBorder || value.isNotBlank())

    val fieldShape = RoundedCornerShape(8.dp)
    val borderColor = when {
        error != null -> Color(0xFFFF4444)
        showFocusBorder -> TvInputFocusBorder
        else -> TvInputBorder
    }
    val borderWidth = if (showFocusBorder) 2.dp else 0.dp
    val fieldHeight = if (singleLine) 48.dp else null

    Box(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = !inputActive,
            textStyle = TextStyle(
                color = Color.White,
                fontFamily = DmSansFamily,
                fontSize = 15.sp
            ),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
            ),
            keyboardActions = KeyboardActions(onDone = { dismissInput() }),
            visualTransformation = if (isPassword && !showPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            cursorBrush = SolidColor(TvFocusAccent),
            modifier = modifier
                .fillMaxWidth()
                .then(if (fieldHeight != null) Modifier.height(fieldHeight) else Modifier)
                .border(borderWidth, borderColor, fieldShape)
                .background(TvInputBg, fieldShape)
                .focusRequester(effectiveFocusRequester)
                .focusable(enabled, interactionSource = interactionSource)
                .lockFocusWhileTyping(inputActive)
                .onPreviewKeyEvent(::handleFieldKey)
                .onKeyEvent(::handleFieldKey)
                .tvFocusScrollIntoView()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled
                ) { activateInput() }
                .onFocusChanged {
                    onHighlightChanged(it.isFocused)
                    if (it.isFocused) {
                        chain?.onItemFocused(chainIndex)
                    }
                }
                .then(
                    if (chain != null && chainIndex >= 0) {
                        Modifier.tvFocusChainNavigation(
                            chain = chain,
                            index = chainIndex,
                            onBack = onNavigateBack,
                            isEditing = { inputActive },
                            onDismissEditing = { dismissInput() }
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(
                    start = 14.dp,
                    end = if (isPassword) 52.dp else 14.dp,
                    top = if (singleLine) 0.dp else 12.dp,
                    bottom = if (singleLine) 0.dp else 12.dp
                ),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fieldHeight != null) Modifier.fillMaxHeight() else Modifier),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TvTextMuted,
                            fontFamily = DmSansFamily,
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            }
        )
        if (isPassword) {
            var showFocused by remember { mutableStateOf(false) }
            GridFocusSurface(
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
    chain: TvFocusChain? = null,
    onNavigateBack: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Color.White else TvTextMuted

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusScrollIntoView()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) chain?.onItemFocused(chainIndex)
            }
            .then(
                if (chain != null && chainIndex >= 0) {
                    Modifier.tvFocusChainNavigation(chain, chainIndex, onNavigateBack)
                } else {
                    Modifier
                }
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        )
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = DmSansFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun TvBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    chainIndex: Int = 0,
    chain: TvFocusChain? = null,
    isEditing: () -> Boolean = { false },
    onDismissEditing: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }

    GridFocusSurface(
        onClick = onClick,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocusScrollIntoView()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) chain?.onItemFocused(chainIndex)
            }
            .then(
                if (chain != null) {
                    Modifier.tvFocusChainNavigation(
                        chain = chain,
                        index = chainIndex,
                        onBack = onClick,
                        isEditing = isEditing,
                        onDismissEditing = onDismissEditing
                    )
                } else {
                    Modifier
                }
            )
            .tvFocusRing(focused = focused, shape = RoundedCornerShape(6.dp), width = 1.5.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
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
