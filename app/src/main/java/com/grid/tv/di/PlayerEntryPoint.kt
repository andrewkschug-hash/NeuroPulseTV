package com.grid.tv.di

import com.grid.tv.feature.sleep.SleepTimerController
import com.grid.tv.cast.ChromecastController
import com.grid.tv.player.DecoderPressureTracker
import com.grid.tv.player.LivePlayerManager
import com.grid.tv.player.PlaybackOrchestrator
import com.grid.tv.player.PlayerFactory
import com.grid.tv.player.VodPlaybackNetworkGuard
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerEntryPoint {
    fun livePlayerManager(): LivePlayerManager
    fun sleepTimerController(): SleepTimerController
    fun chromecastController(): ChromecastController
    fun decoderPressureTracker(): DecoderPressureTracker
    fun playerFactory(): PlayerFactory
    fun playbackOrchestrator(): PlaybackOrchestrator
    fun vodPlaybackNetworkGuard(): VodPlaybackNetworkGuard
}
