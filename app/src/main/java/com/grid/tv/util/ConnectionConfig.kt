package com.grid.tv.util

/** Default connection timeout for playlist import (live channels during connect). */
const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 300

const val MIN_CONNECTION_TIMEOUT_SECONDS = 60
const val MAX_CONNECTION_TIMEOUT_SECONDS = 600

fun connectionTimeoutMs(seconds: Int): Long =
    seconds.coerceIn(MIN_CONNECTION_TIMEOUT_SECONDS, MAX_CONNECTION_TIMEOUT_SECONDS) * 1000L

const val CONNECTION_TIMEOUT_ERROR =
    "Connection timed out. Try a longer timeout in Settings or check your URL."
const val CONNECTION_FAILED_ERROR = "Could not connect. Check your URL and try again."
