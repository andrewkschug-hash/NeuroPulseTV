package com.grid.tv.di

import com.grid.tv.feature.search.MicSearchTrigger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SearchEntryPoint {
    fun micSearchTrigger(): MicSearchTrigger
}
