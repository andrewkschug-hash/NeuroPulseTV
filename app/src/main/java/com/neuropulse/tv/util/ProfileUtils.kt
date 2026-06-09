package com.neuropulse.tv.util

const val MAX_HOUSEHOLD_PROFILES = 5

fun profileInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.size == 1 -> parts[0].first().uppercaseChar().toString()
        else -> "?"
    }
}
