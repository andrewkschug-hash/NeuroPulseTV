package com.grid.tv.data.network.parser

import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.ProgramEntity

data class M3uParseProgress(
    val parsedCount: Int,
    val totalKnown: Int,
    val latest: ChannelEntity? = null,
    val done: Boolean = false,
    val batch: List<ChannelEntity> = emptyList()
)

data class ParsedXmlTv(
    val channelsById: Map<String, String>,
    val programs: List<ProgramEntity>,
    val programCount: Int = programs.size,
)
