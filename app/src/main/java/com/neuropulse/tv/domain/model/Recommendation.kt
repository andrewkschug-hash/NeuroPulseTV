package com.neuropulse.tv.domain.model

data class Recommendation(
    val channel: Channel,
    val score: Double,
    val reason: String
)
