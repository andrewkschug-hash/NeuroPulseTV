package com.grid.tv.di

import com.grid.tv.data.auth.SupabaseAuthRepository
import com.grid.tv.data.auth.SupabaseClientProvider
import com.grid.tv.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {
    fun authRepository(): AuthRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SupabaseEntryPoint {
    fun supabaseClientProvider(): SupabaseClientProvider
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(provider: SupabaseClientProvider): SupabaseClient =
        provider.clientOrNull()
            ?: error("Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindsModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
}
