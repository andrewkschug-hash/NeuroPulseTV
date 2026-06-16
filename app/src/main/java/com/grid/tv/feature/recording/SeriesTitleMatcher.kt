package com.grid.tv.feature.recording

object SeriesTitleMatcher {
    private val seasonEpisodePattern = Regex("""(?i)S(\d+)\s*E(\d+)""")
    private val seasonWordPattern = Regex("""(?i)Season\s+(\d+)""")

    fun matchesProgramTitle(seriesTitle: String, programTitle: String): Boolean {
        val series = seriesTitle.trim()
        val program = programTitle.trim()
        if (series.isBlank() || program.isBlank()) return false
        return program.equals(series, ignoreCase = true) ||
            program.startsWith("$series ", ignoreCase = true) ||
            program.startsWith("$series:", ignoreCase = true) ||
            program.startsWith("$series -", ignoreCase = true) ||
            program.contains(series, ignoreCase = true)
    }

    fun episodeLabel(programTitle: String, seriesTitle: String): String {
        seasonEpisodePattern.find(programTitle)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull()
            if (season != null && episode != null) {
                return "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
            }
        }
        seasonWordPattern.find(programTitle)?.let { match ->
            val season = match.groupValues[1]
            return "Season $season"
        }
        val stripped = programTitle.removePrefix(seriesTitle).trim(' ', '-', ':')
        return stripped.ifBlank { programTitle }
    }

    fun seasonSortKey(programTitle: String): Int {
        seasonEpisodePattern.find(programTitle)?.let {
            return it.groupValues[1].toIntOrNull()?.times(10_000)?.plus(
                it.groupValues[2].toIntOrNull() ?: 0
            ) ?: 0
        }
        seasonWordPattern.find(programTitle)?.let {
            return (it.groupValues[1].toIntOrNull() ?: 0) * 10_000
        }
        return Int.MAX_VALUE
    }
}
