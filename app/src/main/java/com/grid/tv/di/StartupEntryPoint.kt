package com.grid.tv.di

import com.grid.tv.domain.repository.IptvRepository
import com.grid.tv.feature.startup.StartupCoordinator
import com.grid.tv.worker.ChannelHealthScheduler
import com.grid.tv.worker.EpgScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StartupEntryPoint {
    fun epgScheduler(): EpgScheduler
    fun startupCoordinator(): StartupCoordinator
    fun channelHealthScheduler(): ChannelHealthScheduler
    fun repository(): IptvRepository
}
