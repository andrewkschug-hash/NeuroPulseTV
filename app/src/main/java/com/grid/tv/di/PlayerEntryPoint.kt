package com.grid.tv.di

import com.grid.tv.feature.sleep.SleepTimerController
import com.grid.tv.player.LivePlayerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerEntryPoint {
    fun livePlayerManager(): LivePlayerManager
    fun sleepTimerController(): SleepTimerController
}
