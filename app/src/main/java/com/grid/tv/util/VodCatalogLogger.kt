package com.grid.tv.util

import android.util.Log

object VodCatalogLogger {
    private const val TAG = "VodCatalog"

    fun providerConnected(playlistId: Long, hasCredentials: Boolean, hasSecurePassword: Boolean, hasDbPassword: Boolean) {
        Log.i(
            TAG,
            "PROVIDER_CONNECTED playlistId=$playlistId hasCredentials=$hasCredentials " +
                "securePassword=$hasSecurePassword dbPassword=$hasDbPassword"
        )
    }

    fun catalogStageFailure(stage: String, reason: String, dbMovies: Int = 0, dbSeries: Int = 0, filtered: Int = 0) {
        Log.w(
            TAG,
            "CATALOG_STAGE_FAILURE stage=$stage reason=$reason dbMovies=$dbMovies dbSeries=$dbSeries filtered=$filtered"
        )
    }

    fun vodLoadStart(trigger: String) {
        Log.i(TAG, "VOD_LOAD_START trigger=$trigger")
    }

    fun vodLoadComplete(trigger: String, movieCount: Int, seriesCount: Int) {
        Log.i(TAG, "VOD_LOAD_COMPLETE trigger=$trigger movies=$movieCount series=$seriesCount")
    }

    fun moviesReceived(count: Int) {
        Log.i(TAG, "MOVIES_RECEIVED count=$count")
    }

    fun seriesReceived(count: Int) {
        Log.i(TAG, "SERIES_RECEIVED count=$count")
    }

    fun postersReceived(count: Int) {
        Log.i(TAG, "POSTERS_RECEIVED count=$count")
    }

    fun uiItemsRendered(screen: String, count: Int) {
        Log.i(TAG, "UI_ITEMS_RENDERED screen=$screen count=$count")
    }

    fun catalogEmptyReason(screen: String, reason: String, dbMovies: Int, dbSeries: Int, filtered: Int) {
        Log.w(
            TAG,
            "CATALOG_EMPTY screen=$screen reason=$reason dbMovies=$dbMovies dbSeries=$dbSeries filtered=$filtered"
        )
    }
}
