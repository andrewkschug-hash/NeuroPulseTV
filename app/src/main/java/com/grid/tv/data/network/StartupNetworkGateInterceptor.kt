package com.grid.tv.data.network

import com.grid.tv.feature.startup.StartupSafety
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Blocks OkHttp until [StartupSafety] reports the input-safe window. */
@Singleton
class StartupNetworkGateInterceptor @Inject constructor(
    private val startupSafety: StartupSafety
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (!startupSafety.allowNetwork(path)) {
            throw IOException("StartupSafety: network blocked until input safe ($path)")
        }
        return chain.proceed(chain.request())
    }
}
