package com.grid.tv.domain.epg

import android.util.Log
import com.grid.tv.data.db.dao.ChannelDao
import com.grid.tv.data.db.dao.EpgResolutionSuggestionDao
import com.grid.tv.data.db.dao.EpgSourceChannelDao
import com.grid.tv.data.db.dao.ProgramDao
import com.grid.tv.data.db.entity.ChannelEntity
import com.grid.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.grid.tv.data.db.entity.EpgSourceChannelEntity
import com.grid.tv.domain.model.Channel
import com.grid.tv.domain.model.EpgResolutionStatus
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.grid.tv.data.network.AppHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

sealed class EpgResolutionResult {
    data class AutoMatched(
        val epgId: String,
        val epgName: String,
        val confidence: Int,
        val source: String,
        val reason: EpgMatchReason
    ) : EpgResolutionResult()

    data class SuggestedMatch(
        val epgId: String,
        val epgName: String,
        val confidence: Int,
        val source: String,
        val reason: EpgMatchReason
    ) : EpgResolutionResult()

    object NoMatch : EpgResolutionResult()
}

data class EpgResolutionProgress(
    val total: Int,
    val completed: Int,
    val autoMatched: Int,
    val suggested: Int,
    val failed: Int,
    val currentChannel: String
)

@Singleton
class EpgResolverEngine @Inject constructor(
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
    private val sourceDao: EpgSourceChannelDao,
    private val suggestionDao: EpgResolutionSuggestionDao,
    private val appHttpClient: AppHttpClient,
    private val normalizer: ChannelNameNormalizer,
    private val matcher: EpgMatcher,
    private val canonicalSeeder: CanonicalChannelSeeder,
    private val analyticsTracker: EpgMatchAnalyticsTracker
) {
    private val lastExternalRequestAt = AtomicLong(0)

    fun normalizeChannelName(name: String): String = normalizer.normalize(name)

    fun calculateConfidence(channelName: String, epgName: String): Int =
        normalizer.calculateConfidence(channelName, epgName)

    suspend fun resolveChannel(channel: Channel): EpgResolutionResult {
        canonicalSeeder.ensureSeeded()
        val candidates = gatherCandidates(channel)
        Log.d(
            TAG,
            "resolveChannel name=${channel.name} epgId=${channel.epgId} candidates=${candidates.size} " +
                "(xmltv=${candidates.count { it.source.startsWith("xmltv:") }})"
        )
        val outcome = matcher.match(channel.name, channel.epgId, candidates)
        val best = outcome.best ?: run {
            Log.d(TAG, "resolveChannel NO MATCH for ${channel.name}")
            return EpgResolutionResult.NoMatch
        }
        Log.d(
            TAG,
            "resolveChannel ${channel.name} → epgId=${best.epgId} confidence=${best.confidence} " +
                "source=${best.source} reason=${best.reason}"
        )

        return if (best.confidence >= 85) {
            EpgResolutionResult.AutoMatched(
                epgId = best.epgId,
                epgName = best.epgName,
                confidence = best.confidence,
                source = best.source,
                reason = best.reason
            )
        } else if (best.confidence in 55..84) {
            EpgResolutionResult.SuggestedMatch(
                epgId = best.epgId,
                epgName = best.epgName,
                confidence = best.confidence,
                source = best.source,
                reason = best.reason
            )
        } else {
            EpgResolutionResult.NoMatch
        }
    }

    suspend fun previewGuideFixes(createdAfter: Long = 0L): List<EpgFixProposal> {
        canonicalSeeder.ensureSeeded()
        val channels = loadUnresolvedChannels(createdAfter)
        return channels.mapNotNull { entity ->
            val channel = entity.toDomain()
            when (val result = resolveChannel(channel)) {
                is EpgResolutionResult.AutoMatched -> EpgFixProposal(
                    channelId = entity.id,
                    channelName = entity.name,
                    proposedEpgId = result.epgId,
                    proposedEpgName = result.epgName,
                    confidence = result.confidence,
                    reason = result.reason,
                    source = result.source
                )

                is EpgResolutionResult.SuggestedMatch -> EpgFixProposal(
                    channelId = entity.id,
                    channelName = entity.name,
                    proposedEpgId = result.epgId,
                    proposedEpgName = result.epgName,
                    confidence = result.confidence,
                    reason = result.reason,
                    source = result.source
                )

                EpgResolutionResult.NoMatch -> null
            }
        }
    }

    suspend fun applyGuideFixes(proposals: List<EpgFixProposal>) {
        proposals.forEach { proposal ->
            if (proposal.confidence >= 85) {
                channelDao.applyResolution(
                    channelId = proposal.channelId,
                    epgId = proposal.proposedEpgId,
                    status = EpgResolutionStatus.AUTO_MATCHED.name,
                    confidence = proposal.confidence,
                    source = proposal.source,
                    attemptAt = System.currentTimeMillis()
                )
                suggestionDao.clearForChannel(proposal.channelId.toString())
                analyticsTracker.recordAutoMatch(proposal.reason)
                analyticsTracker.saveLearnedMapping(
                    originalName = proposal.channelName,
                    epgId = proposal.proposedEpgId,
                    epgDisplayName = proposal.proposedEpgName,
                    source = proposal.source
                )
            } else {
                channelDao.applyResolution(
                    channelId = proposal.channelId,
                    epgId = null,
                    status = EpgResolutionStatus.SUGGESTED.name,
                    confidence = proposal.confidence,
                    source = proposal.source,
                    attemptAt = System.currentTimeMillis()
                )
                suggestionDao.upsert(
                    EpgResolutionSuggestionEntity(
                        channelId = proposal.channelId.toString(),
                        suggestedEpgId = proposal.proposedEpgId,
                        suggestedEpgName = proposal.proposedEpgName,
                        confidence = proposal.confidence,
                        source = proposal.source,
                        matchReason = proposal.reason.name
                    )
                )
                analyticsTracker.recordSuggested(proposal.reason)
            }
        }
    }

    fun resolveAllUnmatched(createdAfter: Long = 0L): Flow<EpgResolutionProgress> = flow {
        canonicalSeeder.ensureSeeded()
        val allCandidates = loadUnresolvedChannels(createdAfter)
        Log.i(TAG, "resolveAllUnmatched: ${allCandidates.size} channels (createdAfter=$createdAfter)")
        val total = allCandidates.size
        var completed = 0
        var auto = 0
        var sugg = 0
        var failed = 0
        emit(EpgResolutionProgress(total, completed, auto, sugg, failed, ""))

        allCandidates.chunked(50).forEach { chunk ->
            for (entity in chunk) {
                val channel = entity.toDomain()
                when (val result = resolveChannel(channel)) {
                    is EpgResolutionResult.AutoMatched -> {
                        channelDao.applyResolution(
                            entity.id,
                            result.epgId,
                            EpgResolutionStatus.AUTO_MATCHED.name,
                            result.confidence,
                            result.source,
                            System.currentTimeMillis()
                        )
                        suggestionDao.clearForChannel(entity.id.toString())
                        analyticsTracker.recordAutoMatch(result.reason)
                        analyticsTracker.saveLearnedMapping(
                            originalName = entity.name,
                            epgId = result.epgId,
                            epgDisplayName = result.epgName,
                            source = result.source
                        )
                        auto++
                    }

                    is EpgResolutionResult.SuggestedMatch -> {
                        channelDao.applyResolution(
                            entity.id,
                            entity.epgId,
                            EpgResolutionStatus.SUGGESTED.name,
                            result.confidence,
                            result.source,
                            System.currentTimeMillis()
                        )
                        suggestionDao.upsert(
                            EpgResolutionSuggestionEntity(
                                channelId = entity.id.toString(),
                                suggestedEpgId = result.epgId,
                                suggestedEpgName = result.epgName,
                                confidence = result.confidence,
                                source = result.source,
                                matchReason = result.reason.name
                            )
                        )
                        analyticsTracker.recordSuggested(result.reason)
                        sugg++
                    }

                    EpgResolutionResult.NoMatch -> {
                        channelDao.applyResolution(
                            entity.id,
                            entity.epgId,
                            EpgResolutionStatus.UNRESOLVABLE.name,
                            0,
                            null,
                            System.currentTimeMillis()
                        )
                        analyticsTracker.recordUnmatched()
                        failed++
                    }
                }
                completed++
                emit(EpgResolutionProgress(total, completed, auto, sugg, failed, entity.name))
            }
        }
    }

    fun shouldProcessStatus(status: String, lastAttemptAt: Long, rerunUnresolvableBefore: Long): Boolean {
        if (status == EpgResolutionStatus.CONFIRMED.name || status == EpgResolutionStatus.MANUAL.name) return false
        if (status == EpgResolutionStatus.UNRESOLVABLE.name && lastAttemptAt > rerunUnresolvableBefore) return false
        return true
    }

    private suspend fun loadUnresolvedChannels(createdAfter: Long): List<ChannelEntity> {
        val rerunMonthlyBefore = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val allCandidates = mutableListOf<ChannelEntity>()
        var offset = 0
        val batchSize = 50
        while (true) {
            val page = channelDao.unresolvedBatch(batchSize, offset, createdAfter, rerunMonthlyBefore)
            if (page.isEmpty()) break
            allCandidates += page.filter {
                shouldProcessStatus(it.epgResolutionStatus, it.epgLastAttemptAt, rerunMonthlyBefore)
            }
            offset += batchSize
        }
        return allCandidates
    }

    private suspend fun gatherCandidates(channel: Channel): List<EpgSourceChannelEntity> {
        val pool = linkedMapOf<String, EpgSourceChannelEntity>()
        sourceDao.bySource("xmltv:${channel.playlistId}").forEach { pool[it.epgId] = it }
        localCandidates(channel.playlistId).forEach { pool[it.epgId] = it }

        refreshSourceIfStale("epg.best", "https://epg.best/epg.xml.gz")
        sourceDao.bySource("epg.best").forEach { pool[it.epgId] = it }

        detectRegions(channel.name).forEach { region ->
            val source = "i.mjh.nz/$region"
            refreshSourceIfStale(source, "https://i.mjh.nz/$region/epg.xml.gz")
            sourceDao.bySource(source).forEach { pool[it.epgId] = it }
        }

        return pool.values.toList()
    }

    private suspend fun localCandidates(playlistId: Long): List<EpgSourceChannelEntity> {
        return programDao.distinctChannelEpgIdsForPlaylist(playlistId).map {
            EpgSourceChannelEntity(
                epgId = it,
                displayName = it,
                normalizedName = normalizer.normalize(it),
                source = "local:$playlistId",
                logoUrl = null,
                cachedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun refreshSourceIfStale(source: String, url: String) {
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val last = sourceDao.lastCachedAt(source) ?: 0L
        if (System.currentTimeMillis() - last < weekMs) return

        rateLimitExternalRequest()
        val gz = fetchBytes(url)
        val xml = GZIPInputStream(ByteArrayInputStream(gz)).bufferedReader().use { it.readText() }
        val parsed = parseXmltvChannels(xml, source)
        sourceDao.clearBySource(source)
        sourceDao.insertAll(parsed)
    }

    private suspend fun rateLimitExternalRequest() {
        val now = System.currentTimeMillis()
        val last = lastExternalRequestAt.get()
        val waitMs = (100 - (now - last)).coerceAtLeast(0)
        if (waitMs > 0) delay(waitMs)
        lastExternalRequestAt.set(System.currentTimeMillis())
    }

    private fun fetchBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).build()
        appHttpClient.client().newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("EPG source fetch failed: ${response.code}")
            return response.body?.bytes() ?: byteArrayOf()
        }
    }

    private fun parseXmltvChannels(xml: String, source: String): List<EpgSourceChannelEntity> {
        val now = System.currentTimeMillis()
        val channelRegex = Regex("<channel\\s+id=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL)
        val nameRegex = Regex("<display-name[^>]*>(.*?)</display-name>", RegexOption.DOT_MATCHES_ALL)
        val iconRegex = Regex("<icon\\s+src=\\\"([^\\\"]+)\\\"")
        return channelRegex.findAll(xml).mapNotNull { m ->
            val id = m.groupValues[1].trim()
            val body = m.groupValues[2]
            val name = nameRegex.find(body)?.groupValues?.get(1)?.replace(Regex("<.*?>"), "")?.trim().orEmpty()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val icon = iconRegex.find(body)?.groupValues?.get(1)
            EpgSourceChannelEntity(
                epgId = id,
                displayName = name,
                normalizedName = normalizer.normalize(name),
                source = source,
                logoUrl = icon,
                cachedAt = now
            )
        }.toList()
    }

    private fun detectRegions(name: String): List<String> {
        val original = name.lowercase(Locale.getDefault())
        fun has(token: String): Boolean = Regex("\\b$token\\b").containsMatchIn(original)
        val regions = mutableListOf<String>()
        if (has("ca")) regions += "ca"
        if (has("us")) regions += "us"
        if (has("uk")) regions += "uk"
        if (has("au")) regions += "au"
        if (has("nz")) regions += "nz"
        if (regions.isEmpty()) regions += listOf("us", "uk", "ca", "au", "nz")
        return regions
    }

    private fun ChannelEntity.toDomain(): Channel = Channel(
        id = id,
        number = number,
        name = name,
        group = groupName,
        logoUrl = logoUrl,
        epgId = epgId,
        streamUrl = streamUrl,
        playlistId = playlistId,
        isFavorite = false
    )

    companion object {
        private const val TAG = "EpgFlow"
    }
}
