package com.neuropulse.tv.data.network.api

import retrofit2.http.GET
import retrofit2.http.Url

interface PlaylistApi {
    @GET
    suspend fun fetchText(@Url url: String): String
}
