package com.grid.tv.util

import androidx.compose.ui.graphics.Color

const val MAX_HOUSEHOLD_PROFILES = 5

const val DEFAULT_PROFILE_AVATAR_COLOR = "#3B8FFF"

fun profileInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.size == 1 -> parts[0].first().uppercaseChar().toString()
        else -> "?"
    }
}

fun parseProfileAvatarColor(
    hex: String,
    fallbackHex: String = DEFAULT_PROFILE_AVATAR_COLOR
): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrElse {
            runCatching { Color(android.graphics.Color.parseColor(fallbackHex)) }
                .getOrDefault(Color(0xFF3B8FFF))
        }
}
