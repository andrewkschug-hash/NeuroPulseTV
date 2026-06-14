package com.neuropulse.tv.domain.epg

import com.neuropulse.tv.data.db.dao.ChannelDao
import com.neuropulse.tv.data.db.dao.EpgResolutionSuggestionDao
import com.neuropulse.tv.data.db.dao.EpgSourceChannelDao
import com.neuropulse.tv.data.db.dao.ProgramDao
import com.neuropulse.tv.data.db.entity.ChannelEntity
import com.neuropulse.tv.data.db.entity.EpgResolutionSuggestionEntity
import com.neuropulse.tv.data.db.entity.EpgSourceChannelEntity
import com.neuropulse.tv.domain.model.Channel
import com.neuropulse.tv.domain.model.EpgResolutionStatus
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.neuropulse.tv.data.network.AppHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

sealed class EpgResolutionResult {
    data class AutoMatched(val epgId: String, val epgName: String, val confidence: Int, val source: String) : EpgResolutionResult()
    data class SuggestedMatch(val epgId: String, val epgName: String, val confidence: Int, val source: String) : EpgResolutionResult()
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
    private val appHttpClient: AppHttpClient
) {
    private val lastExternalRequestAt = AtomicLong(0)

    fun normalizeChannelName(name: String): String {
        var s = name.lowercase(Locale.getDefault())
        s = s.replace(Regex("\\b(uk|us|ca|au|nz|za|ie|fr)\\b"), " ")
        s = s.replace(Regex("\\[(uk|us|ca|au|nz|za|ie|fr)]"), " ")
        s = s.replace(Regex("\\((uk|us|ca|au|nz|za|ie|fr)\\)"), " ")
        s = s.replace(Regex("\\b(hd|fhd|uhd|4k|sd|lq|hq)\\b"), " ")
        s = s.replace(Regex("\\b(1080p|720p|480p|4320p)\\b"), " ")
        s = s.replace(Regex("\\+[0-9]{1,2}\\b"), " ")
        s = s.replace(Regex("\\b(tv|channel|network|broadcasting)\\b"), " ")
        s = s.replace(Regex("[|\\[\\]()._:\\-+]"), " ")
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    fun calculateConfidence(channelName: String, epgName: String): Int {
        val c = normalizeChannelName(channelName)
        val e = normalizeChannelName(epgName)
        if (c.isBlank() || e.isBlank()) return 0
        if (c == e) return 100
        if (c.contains(e) || e.contains(c)) return 90
        val dist = levenshtein(c, e)
        if (dist == 1) return 85
        if (dist == 2) return 75

        val cWords = c.split(" ").filter { it.isNotBlank() }
        val eWords = e.split(" ").filter { it.isNotBlank() }.toSet()
        val matchCount = cWords.count { eWords.contains(it) }
        if (cWords.isNotEmpty() && matchCount == cWords.size) return 70
        if (cWords.isNotEmpty() && matchCount * 2 > cWords.size) return 55
        return 0
    }

    suspend fun resolveChannel(channel: Channel): EpgResolutionResult {
        val local = localCandidates()
        pickBest(channel.name, local)?.let { best ->
            if (best.third >= 85) return EpgResolutionResult.AutoMatched(best.first, best.second, best.third, "local")
        }

        refreshSourceIfStale("epg.best", "https://epg.best/epg.xml.gz")
        val bestGlobal = pickBest(channel.name, sourceDao.bySource("epg.best"))
        if (bestGlobal != null && bestGlobal.third >= 85) {
            return EpgResolutionResult.AutoMatched(bestGlobal.first, bestGlobal.second, bestGlobal.third, "epg.best")
        }

        val regions = detectRegions(channel.name)
        for (region in regions) {
            val source = "i.mjh.nz/$region"
            refreshSourceIfStale(source, "https://i.mjh.nz/$region/epg.xml.gz")
            val bestRegion = pickBest(channel.name, sourceDao.bySource(source))
            if (bestRegion != null && bestRegion.third >= 85) {
                return EpgResolutionResult.AutoMatched(bestRegion.first, bestRegion.second, bestRegion.third, source)
            }
        }

        val suggestedCandidates = mutableListOf<Triple<String, String, Int>>()
        bestGlobal?.let { suggestedCandidates += Triple(it.first, it.second, it.third) }
        for (region in regions) {
            val source = "i.mjh.nz/$region"
            pickBest(channel.name, sourceDao.bySource(source))?.let { suggestedCandidates += Triple(it.first, it.second, it.third) }
        }
        pickBest(channel.name, local)?.let { suggestedCandidates += Triple(it.first, it.second, it.third) }

        val top = suggestedCandidates.maxByOrNull { it.third }
        return if (top != null && top.third in 55..84) {
            val source = when {
                local.any { it.epgId == top.first } -> "local"
                else -> "epg.best"
            }
            EpgResolutionResult.SuggestedMatch(top.first, top.second, top.third, source)
        } else {
            EpgResolutionResult.NoMatch
        }
    }

    fun resolveAllUnmatched(createdAfter: Long = 0L): Flow<EpgResolutionProgress> = flow {
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

        val total = allCandidates.size
        var completed = 0
        var auto = 0
        var sugg = 0
        var failed = 0
        emit(EpgResolutionProgress(total, completed, auto, sugg, failed, ""))

        allCandidates.chunked(50).forEach { chunk ->
            for (entity in chunk) {
                val channel = Channel(
                    id = entity.id,
                    number = entity.number,
                    name = entity.name,
                    group = entity.groupName,
                    logoUrl = entity.logoUrl,
                    epgId = entity.epgId,
                    streamUrl = entity.streamUrl,
                    playlistId = entity.playlistId,
                    isFavorite = false
                )
                when (val result = resolveChannel(channel)) {
                    is EpgResolutionResult.AutoMatched -> {
                        channelDao.applyResolution(entity.id, result.epgId, EpgResolutionStatus.AUTO_MATCHED.name, result.confidence, result.source, System.currentTimeMillis())
                        suggestionDao.clearForChannel(entity.id.toString())
                        auto++
                    }

                    is EpgResolutionResult.SuggestedMatch -> {
                        channelDao.applyResolution(entity.id, entity.epgId, EpgResolutionStatus.SUGGESTED.name, result.confidence, result.source, System.currentTimeMillis())
                        suggestionDao.upsert(
                            EpgResolutionSuggestionEntity(
                                channelId = entity.id.toString(),
                                suggestedEpgId = result.epgId,
                                suggestedEpgName = result.epgName,
                                confidence = result.confidence,
                                source = result.source
                            )
                        )
                        sugg++
                    }

                    EpgResolutionResult.NoMatch -> {
                        channelDao.applyResolution(entity.id, entity.epgId, EpgResolutionStatus.UNRESOLVABLE.name, 0, null, System.currentTimeMillis())
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

    private suspend fun localCandidates(): List<EpgSourceChannelEntity> {
        return programDao.distinctChannelEpgIds().map {
            EpgSourceChannelEntity(
                epgId = it,
                displayName = it,
                normalizedName = normalizeChannelName(it),
                source = "local",
                logoUrl = null,
                cachedAt = System.currentTimeMillis()
            )
        }
    }

    private fun pickBest(channelName: String, source: List<EpgSourceChannelEntity>): Triple<String, String, Int>? {
        var best: Triple<String, String, Int>? = null
        source.forEach { candidate ->
            val confidence = calculateConfidence(channelName, candidate.displayName)
            if (best == null || confidence > best!!.third) {
                best = Triple(candidate.epgId, candidate.displayName, confidence)
            }
        }
        return best
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
                normalizedName = normalizeChannelName(name),
                source = source,
                logoUrl = icon,
                cachedAt = now
            )
        }.toList()
    }

    private fun detectRegions(name: String): List<String> {
        val original = name.lowercase(Locale.getDefault())
        val regions = mutableListOf<String>()
        fun has(token: String): Boolean = Regex("\\b$token\\b").containsMatchIn(original)
        if (has("ca")) regions += "ca"
        if (has("us")) regions += "us"
        if (has("uk")) regions += "uk"
        if (has("au")) regions += "au"
        if (has("nz")) regions += "nz"
        if (regions.isEmpty()) regions += listOf("us", "uk", "ca", "au", "nz")
        return regions
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
