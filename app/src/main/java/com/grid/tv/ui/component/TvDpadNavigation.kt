package com.grid.tv.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.tv.material3.ClickableSurfaceDefaults
import com.grid.tv.ui.component.GridFocusSurface
import com.grid.tv.ui.theme.DmSansFamily
import com.grid.tv.ui.theme.EpgColors
import com.grid.tv.util.TvRemoteKeyboard
import com.grid.tv.util.TvTextInputSession
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
    return isTvActivateKey(event)
}

/** Enter / D-pad center on TV remotes (Compose key + native keycode). */
internal fun isTvActivateKey(event: KeyEvent): Boolean {
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
  @Suppress("UNUSED_PARAMETER") singleLine: Boolean = true,
    onEditingChanged: (Boolean) -> Unit = {},
    onHighlightChanged: (Boolean) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") imeAction: ImeAction = ImeAction.Done,
    onImeNext: (() -> Unit)? = null,
    onImeDone: (() -> Unit)? = null,
    nextFocusRequester: FocusRequester? = null
) {
    TvDialogTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        keyboardType = keyboardType,
        isPassword = isPassword,
        error = error,
        enabled = enabled,
        focusRequester = focusRequester,
        chainIndex = chainIndex,
        chain = chain,
        onEditingChanged = onEditingChanged,
        onHighlightChanged = onHighlightChanged,
        onNavigateBack = onNavigateBack,
        onConfirmed = {
            onImeDone?.invoke() ?: onImeNext?.invoke()
        },
        imeAction = imeAction,
        nextFocusRequester = nextFocusRequester,
        onImeNext = onImeNext,
        backgroundColor = TvInputBg,
        focusedBorderColor = TvInputFocusBorder,
        unfocusedBorderColor = TvInputBorder,
        textColor = Color.White,
        placeholderColor = TvTextMuted,
        labelColor = TvTextMuted
    )
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
