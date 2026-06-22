package com.grid.tv.data.network

sealed interface EpgXmlTvFetchOutcome {
    data class Success(val result: EpgParsedFetchResult) : EpgXmlTvFetchOutcome

    data class HttpError(
        val httpCode: Int,
        val url: String,
        val bodyPreview: String? = null
    ) : EpgXmlTvFetchOutcome
}
