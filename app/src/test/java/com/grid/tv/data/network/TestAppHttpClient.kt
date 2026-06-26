package com.grid.tv.data.network

import com.grid.tv.feature.startup.StartupSafety
import io.mockk.every
import io.mockk.mockk

fun testAppHttpClient(): AppHttpClient {
    val safety = mockk<StartupSafety>(relaxed = true)
    every { safety.allowNetwork(any()) } returns true
    return AppHttpClient(StartupNetworkGateInterceptor(safety))
}
