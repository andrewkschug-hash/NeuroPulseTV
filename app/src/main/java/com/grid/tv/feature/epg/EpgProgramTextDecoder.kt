package com.grid.tv.feature.epg

import okio.ByteString.Companion.decodeBase64

/**
 * Normalizes Xtream short-EPG text fields. Many providers return [title] and [description]
 * as Base64-encoded UTF-8 instead of plain text.
 */
object EpgProgramTextDecoder {

    private val BASE64_CHARS = Regex("^[A-Za-z0-9+/]+={0,2}$")

    fun decode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (!looksLikeBase64(trimmed)) return trimmed
        return runCatching {
            val decoded = decodeBase64Text(trimmed)?.trim().orEmpty()
            decoded.takeIf { it.isNotEmpty() && isReadableText(decoded) } ?: trimmed
        }.getOrDefault(trimmed)
    }

    private fun decodeBase64Text(value: String): String? {
        var padded = value
        while (padded.length % 4 != 0) {
            padded += "="
        }
        return padded.decodeBase64()?.utf8()
    }

    private fun looksLikeBase64(value: String): Boolean {
        if (value.length < 8 || value.length % 4 != 0) return false
        if (value.contains(' ') || value.contains('\n') || value.contains('\t')) return false
        if (!BASE64_CHARS.matches(value)) return false
        // Avoid decoding short alphanumeric titles that happen to match the charset.
        return value.any { it == '+' || it == '/' } || value.endsWith('=') || value.length >= 16
    }

    private fun isReadableText(value: String): Boolean {
        if (value.any { it == '\uFFFD' }) return false
        val printable = value.count { char ->
            char.isLetterOrDigit() || char.isWhitespace() || char in ".,;:!?'\"()-&/"
        }
        return printable.toFloat() / value.length >= 0.85f
    }
}
