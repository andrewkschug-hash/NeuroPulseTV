package com.neuropulse.tv.ui.component

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neuropulse.tv.domain.model.PlaylistType
import com.neuropulse.tv.ui.theme.DmSansFamily
import com.neuropulse.tv.ui.theme.EpgColors
enum class SettingsFocusPanel { TOP_BAR, LEFT, RIGHT }

enum class SettingsFocusLevel { SIDEBAR, SECTION, INSIDE_CARD }

data class SettingsSectionCard(
    val firstFocusIndex: Int,
    val focusCount: Int
) {
    val lastFocusIndex: Int
        get() = if (focusCount <= 0) firstFocusIndex else firstFocusIndex + focusCount - 1

    val hasFocusableItems: Boolean get() = focusCount > 0 && firstFocusIndex >= 0
}

@Stable
class SettingsFocusChain(
    private val requesterPool: MutableList<FocusRequester>,
    initialCount: Int,
    startIndex: Int = 0
) {
    var itemCount: Int = initialCount
        internal set

    val requesters: List<FocusRequester>
        get() = if (itemCount <= 0) emptyList() else requesterPool.take(itemCount)

    var focusedIndex: Int = if (itemCount <= 0) 0 else startIndex.coerceIn(0, itemCount - 1)
        private set

    val lastIndex: Int get() = (itemCount - 1).coerceAtLeast(0)

    fun moveTo(index: Int) {
        if (itemCount <= 0) return
        val target = index.coerceIn(0, itemCount - 1)
        if (target >= requesterPool.size) {
            Log.w(
                SETTINGS_FOCUS_LOG_TAG,
                "moveTo($index) out of pool bounds (pool=${requesterPool.size}, itemCount=$itemCount)"
            )
            return
        }
        focusedIndex = target
        requesterPool[target].requestFocusSafely()
    }

    fun move(delta: Int) = moveTo(focusedIndex + delta)

    fun onItemFocused(index: Int) {
        focusedIndex = index
    }

    fun resetIndex(index: Int) {
        focusedIndex = if (itemCount <= 0) 0 else index.coerceIn(0, itemCount - 1)
    }
}

@Composable
fun rememberSettingsFocusChain(
    count: Int,
    sectionKey: Int,
    startIndex: Int = 0
): SettingsFocusChain {
    val requesterPool = remember {
        mutableListOf<FocusRequester>()
    }
    if (requesterPool.size < count) {
        repeat(count - requesterPool.size) {
            requesterPool.add(FocusRequester())
        }
    }
    val chain = remember(requesterPool) {
        SettingsFocusChain(requesterPool, count, startIndex)
    }
    chain.itemCount = count
    LaunchedEffect(sectionKey, count) {
        chain.resetIndex(startIndex.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }
    return chain
}

data class SettingsContentFocus(
    val chain: SettingsFocusChain,
    val level: SettingsFocusLevel,
    val focusedSectionIndex: Int,
    val sectionCards: List<SettingsSectionCard>
) {
    fun isFocused(index: Int): Boolean =
        level == SettingsFocusLevel.INSIDE_CARD && chain.focusedIndex == index

    fun isSectionHighlighted(cardIndex: Int): Boolean =
        level == SettingsFocusLevel.SECTION && cardIndex == focusedSectionIndex

    fun isInsideSection(cardIndex: Int): Boolean =
        level == SettingsFocusLevel.INSIDE_CARD && cardIndex == focusedSectionIndex

    fun activeCard(): SettingsSectionCard? = sectionCards.getOrNull(focusedSectionIndex)
}

@Composable
fun settingsFocusModifier(
    chainIndex: Int,
    focus: SettingsContentFocus,
    enabled: Boolean = true
): Modifier {
    if (chainIndex < 0 || chainIndex >= focus.chain.requesters.size) {
        Log.w(
            SETTINGS_FOCUS_LOG_TAG,
            "Focus index $chainIndex out of bounds (chain size ${focus.chain.requesters.size})"
        )
        return Modifier.focusProperties {
            canFocus = false
        }
    }
    return Modifier
        .focusRequester(focus.chain.requesters[chainIndex])
        .focusProperties {
            canFocus = enabled && focus.level == SettingsFocusLevel.INSIDE_CARD
        }
        .onFocusChanged { state ->
            if (state.isFocused) {
                focus.chain.onItemFocused(chainIndex)
            }
        }
}

@Composable
fun SettingsFocusTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    chainIndex: Int,
    focus: SettingsContentFocus,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val highlighted = focus.isFocused(chainIndex)
    val keyboardController = LocalSoftwareKeyboardController.current
    SettingsTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        focused = highlighted,
        singleLine = singleLine,
        modifier = modifier
            .then(settingsFocusModifier(chainIndex, focus))
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (highlighted) {
                            keyboardController?.show()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    )
}

private val PillShape = RoundedCornerShape(6.dp)
private val PillDefaultBorder = Color(0xFF3A3A4A)

@Composable
fun SettingsFocusPill(
    label: String,
    selected: Boolean,
    chainIndex: Int,
    focus: SettingsContentFocus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val highlighted = focus.isFocused(chainIndex)
    val borderWidth = if (highlighted || selected) 2.dp else 1.dp
    val backgroundColor = if (selected) {
        EpgColors.Accent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    val borderColor = when {
        highlighted -> Color.White
        selected -> EpgColors.Accent
        else -> PillDefaultBorder
    }
    Text(
        text = label,
        color = when {
            selected -> Color.White
            highlighted -> EpgColors.TextPrimary
            else -> EpgColors.TextSecondary
        },
        fontFamily = DmSansFamily,
        fontSize = 13.sp,
        fontWeight = if (selected || highlighted) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .then(settingsFocusModifier(chainIndex, focus))
            .focusable()
            .background(backgroundColor, PillShape)
            .border(borderWidth, borderColor, PillShape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (highlighted) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    )
}

@Composable
fun SettingsFocusPillGroup(
    labels: List<String>,
    selectedIndex: Int,
    startChainIndex: Int,
    focus: SettingsContentFocus,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            SettingsFocusPill(
                label = label,
                selected = index == selectedIndex,
                chainIndex = startChainIndex + index,
                focus = focus,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
fun SettingsFocusButton(
    text: String,
    onClick: () -> Unit,
    chainIndex: Int,
    focus: SettingsContentFocus,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingLabel: String = "Connecting...",
    destructive: Boolean = false
) {
    val highlighted = focus.isFocused(chainIndex)
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .then(settingsFocusModifier(chainIndex, focus, enabled && !isLoading))
            .border(
                width = if (highlighted) 2.dp else 0.dp,
                color = when {
                    highlighted && destructive -> Color(0xFFE53935)
                    highlighted -> EpgColors.Accent
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(loadingLabel)
            }
        } else {
            Text(
                text = text,
                color = if (destructive) Color(0xFFE53935) else Color.Unspecified
            )
        }
    }
}

@Composable
fun SettingsFocusToggleRow(
    label: String,
    description: String? = null,
    enabled: Boolean,
    onToggle: () -> Unit,
    chainIndex: Int,
    focus: SettingsContentFocus,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val highlighted = focus.isFocused(chainIndex)

    SettingsToggleRow(
        label = label,
        description = description,
        enabled = enabled,
        focused = highlighted,
        onToggle = onToggle,
        modifier = modifier
            .then(settingsFocusModifier(chainIndex, focus))
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (highlighted) {
                            onToggle()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    )
}

fun connectionsAddFocusCount(playlistType: PlaylistType): Int = when (playlistType) {
    PlaylistType.M3U -> 15
    PlaylistType.XTREAM -> 17
    PlaylistType.STALKER -> 15
}

fun connectionsListFocusCount(playlistCount: Int): Int = 1 + playlistCount * 2

fun connectionsFocusCount(playlistType: PlaylistType, playlistCount: Int): Int =
    connectionsListFocusCount(playlistCount) + connectionsAddFocusCount(playlistType)

/** GuideSettingsContent chain indices 0..19. */
const val GUIDE_FOCUS_COUNT = 20

/**
 * PlaybackSettingsContent chain indices 0..28:
 * 0-2 retries, 3-6 quality, 7-9 buffer, 10-11 toggles, 12-15 aspect, 16-21 subtitles, 22-28 audio/sleep.
 */
const val PLAYBACK_FOCUS_COUNT = 29
private const val PLAYBACK_CARD0_COUNT = 12
private const val PLAYBACK_CARD1_COUNT = 4
private const val PLAYBACK_CARD2_COUNT = 6
private const val PLAYBACK_CARD3_COUNT =
    PLAYBACK_FOCUS_COUNT - PLAYBACK_CARD0_COUNT - PLAYBACK_CARD1_COUNT - PLAYBACK_CARD2_COUNT

/** InterfaceSettingsContent chain indices 0..11. */
const val INTERFACE_FOCUS_COUNT = 12

/** AboutSettingsContent chain indices 0..3. */
const val ABOUT_FOCUS_COUNT = 4

private const val SETTINGS_FOCUS_LOG_TAG = "SettingsFocus"

fun guideFocusCount(): Int = GUIDE_FOCUS_COUNT

fun playbackFocusCount(): Int = PLAYBACK_FOCUS_COUNT

fun interfaceFocusCount(): Int = INTERFACE_FOCUS_COUNT

fun aboutFocusCount(): Int = ABOUT_FOCUS_COUNT

/** Horizontal pill groups per settings section (inclusive ranges). */
fun settingsHorizontalPillGroups(
    kind: SettingsSectionKind,
    connectionsFormStart: Int = 0,
    connectionsPlaylistType: PlaylistType = PlaylistType.M3U
): List<IntRange> = when (kind) {
    SettingsSectionKind.Profile -> listOf(3..6)
    SettingsSectionKind.Connections -> {
        val timeoutStart = connectionsFormStart +
            if (connectionsPlaylistType == PlaylistType.M3U) 6 else 8
        listOf(
            (connectionsFormStart + 1)..(connectionsFormStart + 2),
            timeoutStart..(timeoutStart + 2)
        )
    }
    SettingsSectionKind.Guide -> listOf(2..4, 5..6, 7..11, 12..15)
    SettingsSectionKind.Playback -> listOf(3..6, 7..9, 12..15, 18..20)
    SettingsSectionKind.Interface -> listOf(1..4, 6..8, 9..11)
    else -> emptyList()
}

fun handleSettingsHorizontalKey(
    kind: SettingsSectionKind,
    currentIndex: Int,
    key: Key,
    chain: SettingsFocusChain,
    connectionsFormStart: Int = 0,
    connectionsPlaylistType: PlaylistType = PlaylistType.M3U
): Boolean {
    for (range in settingsHorizontalPillGroups(kind, connectionsFormStart, connectionsPlaylistType)) {
        if (currentIndex !in range) continue
        return when (key) {
            Key.DirectionLeft -> {
                if (currentIndex > range.first) chain.moveTo(currentIndex - 1)
                true
            }
            Key.DirectionRight -> {
                if (currentIndex < range.last) chain.moveTo(currentIndex + 1)
                true
            }
            else -> false
        }
    }
    return false
}

fun moveSettingsVerticalFocus(
    currentIndex: Int,
    delta: Int,
    focusedSectionIndex: Int,
    sectionCards: List<SettingsSectionCard>,
    chain: SettingsFocusChain,
    onSectionChange: (Int) -> Unit
): Boolean {
    val card = sectionCards.getOrNull(focusedSectionIndex) ?: return false
    val target = currentIndex + delta
    if (target in card.firstFocusIndex..card.lastFocusIndex) {
        chain.moveTo(target)
        return true
    }
    if (delta > 0 && currentIndex >= card.lastFocusIndex && focusedSectionIndex < sectionCards.lastIndex) {
        val next = sectionCards[focusedSectionIndex + 1]
        if (next.hasFocusableItems) {
            onSectionChange(focusedSectionIndex + 1)
            chain.moveTo(next.firstFocusIndex)
            return true
        }
    }
    if (delta < 0 && currentIndex <= card.firstFocusIndex && focusedSectionIndex > 0) {
        val prev = sectionCards[focusedSectionIndex - 1]
        if (prev.hasFocusableItems) {
            onSectionChange(focusedSectionIndex - 1)
            chain.moveTo(prev.lastFocusIndex)
            return true
        }
    }
    return false
}

/** Swatch grid: indices [swatchStart .. swatchStart + swatchCount - 1], 4 columns. */
fun moveProfileSwatchFocus(
    currentIndex: Int,
    swatchStart: Int,
    swatchCount: Int,
    key: Key
): Int? {
    if (currentIndex !in swatchStart until swatchStart + swatchCount) return null
    val local = currentIndex - swatchStart
    val col = local % 4
    val row = local / 4
    val maxRow = (swatchCount - 1) / 4
    return when (key) {
        Key.DirectionLeft -> if (col > 0) currentIndex - 1 else null
        Key.DirectionRight -> if (col < 3 && local + 1 < swatchCount) currentIndex + 1 else null
        Key.DirectionDown -> if (row < maxRow && local + 4 < swatchCount) currentIndex + 4 else null
        Key.DirectionUp -> if (row > 0) currentIndex - 4 else null
        else -> null
    }
}

fun profileContentFocusCount(
    profileCount: Int,
    activeProfileId: Long?,
    hasActiveProfile: Boolean
): Int {
    val swatchCount = if (hasActiveProfile) 8 else 0
    val useButtons = profileCount - if (activeProfileId != null) 1 else 0
    return 1 + swatchCount + useButtons.coerceAtLeast(0) + 7
}

fun buildProfileSectionCards(
    profileCount: Int,
    activeProfileId: Long?,
    hasActiveProfile: Boolean,
    contentFocusCount: Int,
    swatchStart: Int = 1,
    swatchCount: Int = 8
): List<SettingsSectionCard> {
    val swatches = if (hasActiveProfile) swatchCount else 0
    val useButtons = (profileCount - if (activeProfileId != null) 1 else 0).coerceAtLeast(0)
    val parentalStart = (contentFocusCount - 7).coerceAtLeast(0)
    return buildList {
        add(SettingsSectionCard(firstFocusIndex = 0, focusCount = 1))
        if (hasActiveProfile) {
            add(SettingsSectionCard(firstFocusIndex = swatchStart, focusCount = swatches))
        }
        add(
            if (useButtons > 0) {
                SettingsSectionCard(firstFocusIndex = 1 + swatches, focusCount = useButtons)
            } else {
                SettingsSectionCard(firstFocusIndex = -1, focusCount = 0)
            }
        )
        add(SettingsSectionCard(firstFocusIndex = parentalStart, focusCount = 7))
    }
}

enum class SettingsSectionKind {
    Profile, Connections, Guide, Playback, Interface, Recordings, About
}

fun buildSettingsSectionCards(
    kind: SettingsSectionKind,
    contentFocusCount: Int,
    playlistCount: Int = 0,
    connectionsPlaylistType: PlaylistType = PlaylistType.M3U,
    connectionsShowForm: Boolean = false,
    profileCount: Int = 0,
    activeProfileId: Long? = null,
    hasActiveProfile: Boolean = false,
    storageOptionCount: Int = 0
): List<SettingsSectionCard> = when (kind) {
    SettingsSectionKind.Profile -> buildProfileSectionCards(
        profileCount = profileCount,
        activeProfileId = activeProfileId,
        hasActiveProfile = hasActiveProfile,
        contentFocusCount = contentFocusCount
    )
    SettingsSectionKind.Connections -> {
        if (connectionsShowForm) {
            listOf(
                SettingsSectionCard(
                    firstFocusIndex = 0,
                    focusCount = connectionsAddFocusCount(connectionsPlaylistType)
                )
            )
        } else {
            listOf(
                SettingsSectionCard(
                    firstFocusIndex = 0,
                    focusCount = connectionsListFocusCount(playlistCount)
                )
            )
        }
    }
    SettingsSectionKind.Guide -> listOf(
        SettingsSectionCard(firstFocusIndex = 0, focusCount = 2),
        SettingsSectionCard(firstFocusIndex = 2, focusCount = 3),
        SettingsSectionCard(firstFocusIndex = 5, focusCount = 13),
        SettingsSectionCard(firstFocusIndex = 18, focusCount = 2)
    )
    SettingsSectionKind.Playback -> listOf(
        SettingsSectionCard(firstFocusIndex = 0, focusCount = PLAYBACK_CARD0_COUNT),
        SettingsSectionCard(
            firstFocusIndex = PLAYBACK_CARD0_COUNT,
            focusCount = PLAYBACK_CARD1_COUNT
        ),
        SettingsSectionCard(
            firstFocusIndex = PLAYBACK_CARD0_COUNT + PLAYBACK_CARD1_COUNT,
            focusCount = PLAYBACK_CARD2_COUNT
        ),
        SettingsSectionCard(
            firstFocusIndex = PLAYBACK_CARD0_COUNT + PLAYBACK_CARD1_COUNT + PLAYBACK_CARD2_COUNT,
            focusCount = PLAYBACK_CARD3_COUNT
        )
    )
    SettingsSectionKind.Interface -> listOf(
        SettingsSectionCard(firstFocusIndex = 0, focusCount = 5),
        SettingsSectionCard(firstFocusIndex = 5, focusCount = 1),
        SettingsSectionCard(firstFocusIndex = 6, focusCount = 6)
    )
    SettingsSectionKind.Recordings -> listOf(
        SettingsSectionCard(firstFocusIndex = 0, focusCount = storageOptionCount)
    )
    SettingsSectionKind.About -> listOf(
        SettingsSectionCard(firstFocusIndex = 0, focusCount = 2),
        SettingsSectionCard(firstFocusIndex = 2, focusCount = 1),
        SettingsSectionCard(firstFocusIndex = 3, focusCount = 1)
    )
}
