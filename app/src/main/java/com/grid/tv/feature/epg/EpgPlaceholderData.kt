package com.grid.tv.feature.epg

import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.EpgResolutionStatus
import com.grid.tv.domain.model.Program
import com.grid.tv.domain.model.ProgramGenre

/**
 * Demo EPG data for layout testing when no real channels are loaded yet.
 * Remove usage once playlist parsing populates the grid.
 */
object EpgPlaceholderData {

    private val channelDefs = listOf(
        Triple(2, "CBC", "cbc") to "Canada | Entertainment",
        Triple(3, "CTV", "ctv") to "Canada | Entertainment",
        Triple(5, "Global", "global") to "Canada | Entertainment",
        Triple(9, "City TV", "citytv") to "Canada | Entertainment",
        Triple(25, "TSN", "tsn") to "Canada | Sports",
        Triple(28, "Sportsnet", "sportsnet") to "Canada | Sports",
        Triple(40, "Discovery", "discovery") to "USA | Entertainment",
        Triple(45, "CNN", "cnn") to "USA | News"
    )

    private val programDefs = mapOf(
        "cbc" to listOf(
            Triple("The National", 60, ProgramGenre.NEWS),
            Triple("Marketplace", 30, ProgramGenre.GENERAL),
            Triple("Schitt's Creek", 30, ProgramGenre.GENERAL),
            Triple("Doc Zone", 60, ProgramGenre.GENERAL)
        ),
        "ctv" to listOf(
            Triple("CTV News", 60, ProgramGenre.NEWS),
            Triple("The Amazing Race", 60, ProgramGenre.GENERAL),
            Triple("MasterChef Canada", 60, ProgramGenre.GENERAL),
            Triple("Late Night", 30, ProgramGenre.GENERAL)
        ),
        "global" to listOf(
            Triple("Global National", 30, ProgramGenre.NEWS),
            Triple("Entertainment Tonight", 30, ProgramGenre.GENERAL),
            Triple("NCIS", 60, ProgramGenre.GENERAL),
            Triple("Hockey Central", 30, ProgramGenre.SPORTS)
        ),
        "citytv" to listOf(
            Triple("CityNews", 30, ProgramGenre.NEWS),
            Triple("Bob's Burgers", 30, ProgramGenre.KIDS),
            Triple("Modern Family", 30, ProgramGenre.GENERAL),
            Triple("Cityline", 60, ProgramGenre.GENERAL)
        ),
        "tsn" to listOf(
            Triple("SportsCentre", 60, ProgramGenre.SPORTS),
            Triple("NHL Tonight", 60, ProgramGenre.SPORTS),
            Triple("That’s Hockey", 30, ProgramGenre.SPORTS),
            Triple("NBA Game Night", 120, ProgramGenre.SPORTS)
        ),
        "sportsnet" to listOf(
            Triple("Hockey Night in Canada", 180, ProgramGenre.SPORTS),
            Triple("MLB Tonight", 60, ProgramGenre.SPORTS),
            Triple("Soccer Central", 30, ProgramGenre.SPORTS),
            Triple("Blue Jays Baseball", 180, ProgramGenre.SPORTS)
        ),
        "discovery" to listOf(
            Triple("Gold Rush", 60, ProgramGenre.GENERAL),
            Triple("Deadliest Catch", 60, ProgramGenre.GENERAL),
            Triple("How It's Made", 30, ProgramGenre.GENERAL),
            Triple("MythBusters", 60, ProgramGenre.GENERAL)
        ),
        "cnn" to listOf(
            Triple("CNN Newsroom", 60, ProgramGenre.NEWS),
            Triple("Anderson Cooper 360", 60, ProgramGenre.NEWS),
            Triple("The Situation Room", 60, ProgramGenre.NEWS),
            Triple("Fareed Zakaria GPS", 60, ProgramGenre.NEWS)
        )
    )

    fun channels(): List<Channel> = channelDefs.mapIndexed { index, (triple, group) ->
        val (number, name, epgId) = triple
        Channel(
            id = -(index + 1).toLong(),
            number = number,
            name = name,
            group = group,
            logoUrl = null,
            epgId = epgId,
            streamUrl = "",
            playlistId = -1,
            isFavorite = false,
            currentProgram = programDefs[epgId]?.firstOrNull()?.first,
            epgResolutionStatus = EpgResolutionStatus.UNRESOLVED
        )
    }

    fun programs(windowStart: Long, windowEnd: Long): List<Program> {
        val result = mutableListOf<Program>()
        var id = -1L
        channelDefs.forEach { (triple, _) ->
            val (_, _, epgId) = triple
            val defs = programDefs[epgId] ?: return@forEach
            var cursor = windowStart
            var defIndex = 0
            while (cursor < windowEnd) {
                val def = defs[defIndex % defs.size]
                val durationMs = def.second * 60_000L
                val end = (cursor + durationMs).coerceAtMost(windowEnd)
                if (end > cursor) {
                    result += Program(
                        id = id--,
                        channelEpgId = epgId,
                        title = def.first,
                        description = "Placeholder program for ${epgId.uppercase()}",
                        startTime = cursor,
                        endTime = end,
                        genre = def.third,
                        catchupUrl = null
                    )
                }
                cursor = end
                defIndex++
            }
        }
        return result
    }
}
