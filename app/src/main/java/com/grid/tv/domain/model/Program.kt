package com.grid.tv.domain.model

enum class ProgramGenre { NEWS, SPORTS, MOVIES, KIDS, GENERAL }

data class Program(
    val id: Long,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val genre: ProgramGenre,
    val catchupUrl: String?
)
