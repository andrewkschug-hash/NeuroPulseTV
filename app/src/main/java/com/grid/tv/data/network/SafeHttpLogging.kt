package com.grid.tv.data.network

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

private val CREDENTIAL_PATTERN = Regex("""(?i)(password|username)=[^&\s"]+""")

fun redactCredentials(message: String): String =
    message.replace(CREDENTIAL_PATTERN) { match ->
        val key = match.value.substringBefore('=')
        "$key=***"
    }

fun createSafeHttpLogger(tag: String = "OkHttp"): HttpLoggingInterceptor.Logger =
    HttpLoggingInterceptor.Logger { message ->
        Log.i(tag, redactCredentials(message))
    }
