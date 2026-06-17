package com.grid.tv.di

import com.grid.tv.feature.health.intelligence.StreamHealthScoringEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {
    @Provides
    @Singleton
    fun provideStreamHealthScoringEngine(): StreamHealthScoringEngine = StreamHealthScoringEngine()
}
