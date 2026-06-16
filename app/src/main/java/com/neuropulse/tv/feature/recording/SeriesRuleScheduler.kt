package com.neuropulse.tv.feature.recording

import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.ProgramDao
import com.neuropulse.tv.data.db.dao.RecordedMediaDao
import com.neuropulse.tv.data.db.dao.ScheduledRecordingDao
import com.neuropulse.tv.data.db.dao.SeriesRecordingRuleDao
import com.neuropulse.tv.data.db.entity.ProgramEntity
import com.neuropulse.tv.data.db.entity.ScheduledRecordingEntity
import com.neuropulse.tv.data.db.entity.SeriesRecordingRuleEntity
import com.neuropulse.tv.domain.model.SeriesEpisode
import com.neuropulse.tv.domain.repository.IptvRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRuleScheduler @Inject constructor(
    private val ruleDao: SeriesRecordingRuleDao,
    private val programDao: ProgramDao,
    private val channelDao: ChannelDao,
    private val scheduledRecordingDao: ScheduledRecordingDao,
    private val recordedMediaDao: RecordedMediaDao,
    private val recordingScheduler: RecordingScheduler,
    private val repository: IptvRepository
) {
    data class ApplySummary(
        val scheduledCount: Int,
        val conflictCount: Int
    )

    suspend fun createRule(
        seriesTitle: String,
        seriesId: Long?,
        playlistId: Long,
        recordNewOnly: Boolean = true,
        paddingStartMins: Int = 2,
        paddingEndMins: Int = 5,
        maxEpisodesToKeep: Int = 0
    ): Long {
        val ruleId = ruleDao.insert(
            SeriesRecordingRuleEntity(
                seriesTitle = seriesTitle.trim(),
                seriesId = seriesId,
                recordNewOnly = recordNewOnly,
                playlistId = playlistId,
                paddingStartMins = paddingStartMins,
                paddingEndMins = paddingEndMins,
                maxEpisodesToKeep = maxEpisodesToKeep,
                createdAt = System.currentTimeMillis()
            )
        )
        applyRulesAfterEpgRefresh()
        return ruleId
    }

    suspend fun deleteRule(ruleId: Long) {
        ruleDao.deleteById(ruleId)
    }

    suspend fun applyRulesAfterEpgRefresh(): ApplySummary {
        val now = System.currentTimeMillis()
        var scheduledCount = 0
        var conflictCount = 0
        ruleDao.getAll().forEach { rule ->
            runCatching {
                val result = applyRule(rule, now)
                scheduledCount += result.scheduledCount
                conflictCount += result.conflictCount
            }
        }
        return ApplySummary(scheduledCount = scheduledCount, conflictCount = conflictCount)
    }

    private suspend fun applyRule(rule: SeriesRecordingRuleEntity, now: Long): ApplySummary {
        if (isAtEpisodeLimit(rule)) return ApplySummary(0, 0)

        val channelsByEpg = channelDao.getByPlaylist(rule.playlistId)
            .filter { !it.epgId.isNullOrBlank() }
            .associateBy { it.epgId!! }

        val xtreamEpisodes = rule.seriesId?.let { seriesId ->
            runCatching { repository.seriesSeasons(seriesId).flatMap { it.episodes } }
                .getOrDefault(emptyList())
        }.orEmpty()

        val candidates = programDao.findUpcomingBySeriesTitle(now, rule.seriesTitle)
            .filter { program ->
                SeriesTitleMatcher.matchesProgramTitle(rule.seriesTitle, program.title)
            }
            .sortedBy { it.startTime }

        var scheduledCount = 0
        var conflictCount = 0
        for (program in candidates) {
            if (isAtEpisodeLimit(rule)) break
            val decision = scheduleProgramIfNeeded(rule, program, channelsByEpg, xtreamEpisodes)
            if (decision?.allowed == true) scheduledCount++
            if (decision?.allowed == false) conflictCount++
        }
        return ApplySummary(scheduledCount = scheduledCount, conflictCount = conflictCount)
    }

    private suspend fun isAtEpisodeLimit(rule: SeriesRecordingRuleEntity): Boolean {
        if (rule.maxEpisodesToKeep <= 0) return false
        val saved = recordedMediaDao.countMatchingSeriesTitle(rule.seriesTitle)
        val queued = scheduledRecordingDao.countUpcomingMatchingSeries(rule.seriesTitle)
        return saved + queued >= rule.maxEpisodesToKeep
    }

    private suspend fun scheduleProgramIfNeeded(
        rule: SeriesRecordingRuleEntity,
        program: ProgramEntity,
        channelsByEpg: Map<String, com.neuropulse.tv.data.db.entity.ChannelEntity>,
        xtreamEpisodes: List<SeriesEpisode>
    ): ConflictDecision? {
        val channel = channelsByEpg[program.channelEpgId] ?: return null

        val paddedStart = program.startTime - rule.paddingStartMins * 60_000L
        val paddedEnd = program.endTime + rule.paddingEndMins * 60_000L

        if (scheduledRecordingDao.countExisting(channel.id, paddedStart, program.title) > 0) {
            return null
        }

        if (rule.recordNewOnly) {
            val alreadyRecorded = recordedMediaDao.hasRecordedEpisode(
                programTitle = program.title,
                programStartTime = program.startTime
            )
            if (alreadyRecorded) return null
        }

        val streamUrl = resolveStreamUrl(program, xtreamEpisodes, channel.streamUrl)
        val item = ScheduledRecordingEntity(
            channelId = channel.id,
            programTitle = program.title,
            startTime = paddedStart,
            endTime = paddedEnd,
            streamUrl = streamUrl,
            channelName = channel.name,
            status = RecordingStatus.SCHEDULED.name
        )
        return recordingScheduler.scheduleOrConflict(item)
    }

    private fun resolveStreamUrl(
        program: ProgramEntity,
        xtreamEpisodes: List<SeriesEpisode>,
        fallbackUrl: String
    ): String {
        if (xtreamEpisodes.isEmpty()) return fallbackUrl
        val normalizedProgramTitle = program.title.trim().lowercase()
        return xtreamEpisodes.firstOrNull { episode ->
            val episodeTitle = episode.title.trim().lowercase()
            episodeTitle == normalizedProgramTitle ||
                normalizedProgramTitle.contains(episodeTitle) ||
                episodeTitle.contains(normalizedProgramTitle)
        }?.streamUrl?.takeIf { it.isNotBlank() } ?: fallbackUrl
    }
}
