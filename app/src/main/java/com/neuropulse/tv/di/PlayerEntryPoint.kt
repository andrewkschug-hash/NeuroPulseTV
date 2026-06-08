package com.neuropulse.tv.di

import com.neuropulse.tv.feature.sleep.SleepTimerController
import com.neuropulse.tv.player.LivePlayerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerEntryPoint {
    fun livePlayerManager(): LivePlayerManager
    fun sleepTimerController(): SleepTimerController
}
