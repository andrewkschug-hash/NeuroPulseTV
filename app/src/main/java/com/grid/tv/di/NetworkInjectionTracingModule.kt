package com.grid.tv.di

import com.grid.tv.data.network.AppHttpClient
import com.grid.tv.data.network.StartupNetworkGateInterceptor
import com.grid.tv.feature.startup.StartupDependencyProbe
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkInjectionTracingModule {

    @Provides
    @Singleton
    fun provideAppHttpClient(
        startupNetworkGateProvider: Provider<StartupNetworkGateInterceptor>
    ): AppHttpClient =
        StartupDependencyProbe.traceCreate("AppHttpClient") {
            AppHttpClient(startupNetworkGateProvider.get())
        }
}
