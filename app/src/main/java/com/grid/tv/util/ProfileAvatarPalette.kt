package com.grid.tv.util

import androidx.compose.ui.graphics.Color
import com.grid.tv.domain.model.AppThemeId
import com.grid.tv.ui.theme.AppThemes

/** Muted purple-gray — default profile fill; distinct from UI focus blue (#3B8FFF). */
const val DEFAULT_PROFILE_AVATAR_COLOR = "#5B4E7A"

/** Guest profile fill — warm slate, never matches the blue focus ring. */
const val GUEST_PROFILE_AVATAR_COLOR = "#6B6560"

/**
 * Theme focus / accent hex values users must not pick for profile avatars.
 * Avatars use [DEFAULT_PROFILE_AVATAR_COLOR] or [ProfileAvatarColors] only.
 */
private val FOCUS_HIGHLIGHT_HEXES: Set<String> = buildSet {
    AppThemeId.entries.forEach { themeId ->
        val palette = AppThemes.palette(themeId)
        add(colorToHex(palette.focusBorder).uppercase())
        add(colorToHex(palette.accent).uppercase())
        add(colorToHex(palette.selectedFill).uppercase())
    }
    addAll(
        listOf(
            "#3B8FFF",
            "#5AA9FF",
            "#1E90FF",
            "#2196F3",
            "#4A9FFF",
            "#2E8CFF",
            "#007AFF",
            "#0A84FF"
        )
    )
}

/** Curated profile avatar swatches — no focus-highlight blues. */
val ProfileAvatarColors: List<Color> = listOf(
    Color(0xFF5B4E7A),
    Color(0xFF1A3D2B),
    Color(0xFF6B3A3A),
    Color(0xFF4A3D6B),
    Color(0xFF1A4D5C),
    Color(0xFF5C4A2A),
    Color(0xFFC45C7A),
    Color(0xFF7A4E8B)
)

fun colorToHex(color: Color): String {
    val argb = color.value.toInt()
    return String.format("#%06X", argb and 0xFFFFFF)
}

fun isConflictingWithFocusHighlight(hex: String): Boolean {
    val normalized = hex.trim().uppercase()
    if (normalized in FOCUS_HIGHLIGHT_HEXES) return true
    val color = runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
        ?: return false
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.value.toInt(), hsv)
    val hue = hsv[0]
    val saturation = hsv[1]
    val value = hsv[2]
    return hue in 195f..225f && saturation >= 0.45f && value >= 0.45f
}

fun sanitizeProfileAvatarColorHex(hex: String?): String {
    val trimmed = hex?.trim().orEmpty()
    if (trimmed.isEmpty() || isConflictingWithFocusHighlight(trimmed)) {
        return DEFAULT_PROFILE_AVATAR_COLOR
    }
    return trimmed
}

fun resolveProfileAvatarColorHex(
    avatarColor: String?,
    isGuestSession: Boolean = false
): String = when {
    isGuestSession -> GUEST_PROFILE_AVATAR_COLOR
    else -> sanitizeProfileAvatarColorHex(avatarColor)
}

fun parseProfileAvatarColor(
    hex: String,
    fallbackHex: String = DEFAULT_PROFILE_AVATAR_COLOR
): Color {
    val resolved = sanitizeProfileAvatarColorHex(hex.ifBlank { fallbackHex })
    return runCatching { Color(android.graphics.Color.parseColor(resolved)) }
        .getOrElse {
            runCatching { Color(android.graphics.Color.parseColor(fallbackHex)) }
                .getOrDefault(Color(0xFF5B4E7A))
        }
}

fun profileAvatarColorForIndex(index: Int): Color =
    ProfileAvatarColors[index % ProfileAvatarColors.size]
