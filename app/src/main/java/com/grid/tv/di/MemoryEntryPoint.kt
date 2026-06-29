package com.grid.tv.di

import com.grid.tv.feature.startup.MemoryPressureHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemoryEntryPoint {
    fun memoryPressureHandler(): MemoryPressureHandler
}
