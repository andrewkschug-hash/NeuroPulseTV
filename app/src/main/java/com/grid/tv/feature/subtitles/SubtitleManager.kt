package com.grid.tv.feature.subtitles

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grid.tv.domain.model.AppSettings
import com.grid.tv.feature.enrichment.TitleEnrichmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SubtitleSource { EMBEDDED, LOCAL, ONLINE }

data class SubtitleRequest(
    val mediaUrl: String,
    val title: String,
    val imdbId: String? = null,
    val providerKey: String? = null,
    val isTv: Boolean = false,
    val releaseYear: Int? = null
)

data class ActiveSubtitle(
    val source: SubtitleSource,
    val language: String,
    val label: String
)

data class ExternalSubtitleFile(
    val file: File,
    val language: String,
    val mimeType: String
)

@Singleton
class SubtitleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subtitleRepository: SubtitleRepository,
    private val localScanner: LocalSubtitleScanner,
    private val titleEnrichmentRepository: TitleEnrichmentRepository,
    private val styleApplicator: SubtitleStyleApplicator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val offsetCacheDir = File(context.cacheDir, "subtitle_offset")

    suspend fun attachAutoSubtitles(
        player: ExoPlayer,
        playerView: PlayerView?,
        request: SubtitleRequest,
        settings: AppSettings
    ): ActiveSubtitle? = withContext(Dispatchers.Main) {
        styleApplicator.apply(playerView, settings)
        if (!settings.subtitlesEnabled) {
            disableTextTracks(player)
            return@withContext null
        }

        val priorities = SubtitleLanguageResolver.priorityLanguages(settings.subtitleLanguage)
        val imdbId = request.imdbId ?: resolveImdbId(request)

        findEmbeddedTrack(player, priorities)?.let { (active, override) ->
            applyEmbeddedOverride(player, override, active.language)
            prefetchOnline(request, imdbId, priorities)
            return@withContext active
        }

        findLocalSubtitle(request, priorities, settings)?.let { (active, external) ->
            applyExternalSubtitle(player, request.mediaUrl, external, settings)
            prefetchOnline(request, imdbId, priorities)
            return@withContext active
        }

        val online = withContext(Dispatchers.IO) {
            subtitleRepository.resolveForPlayback(
                title = request.title,
                imdbId = imdbId,
                languages = priorities,
                releaseYear = request.releaseYear
            )
        }
        if (online != null) {
            val active = ActiveSubtitle(
                source = SubtitleSource.ONLINE,
                language = online.language,
                label = "Online (${online.language})"
            )
            applyExternalSubtitle(player, request.mediaUrl, online, settings)
            return@withContext active
        }

        prefetchOnline(request, imdbId, priorities)
        scope.launch {
            val background = subtitleRepository.resolveForPlayback(
                title = request.title,
                imdbId = imdbId,
                languages = priorities,
                releaseYear = request.releaseYear
            ) ?: return@launch
            withContext(Dispatchers.Main) {
                applyExternalSubtitle(player, request.mediaUrl, background, settings)
            }
        }
        null
    }

    fun applyStyle(playerView: PlayerView?, settings: AppSettings) {
        styleApplicator.apply(playerView, settings)
    }

    fun setEnabled(player: ExoPlayer, enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    private fun findEmbeddedTrack(
        player: ExoPlayer,
        priorities: List<String>
    ): Pair<ActiveSubtitle, TrackSelectionOverride>? {
        val candidates = mutableListOf<Pair<EmbeddedTrack, String>>()
        player.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_TEXT || !group.isSupported) return@forEach
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val language = SubtitleLanguageResolver.normalizeCode(format.language) ?: "und"
                candidates += EmbeddedTrack(group, index, format) to language
            }
        }
        if (candidates.isEmpty()) return null
        val languages = candidates.map { it.second }
        val chosen = SubtitleLanguageResolver.pickBestLanguage(languages, priorities) ?: return null
        val match = candidates.firstOrNull { (_, code) ->
            code == SubtitleLanguageResolver.normalizeCode(chosen) ||
                code.startsWith(chosen)
        } ?: return null
        val track = match.first
        val label = track.format.label?.takeIf { it.isNotBlank() } ?: chosen
        return ActiveSubtitle(SubtitleSource.EMBEDDED, chosen, label) to
            TrackSelectionOverride(track.group.mediaTrackGroup, track.index)
    }

    private fun applyEmbeddedOverride(
        player: ExoPlayer,
        override: TrackSelectionOverride,
        language: String
    ) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(language)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .addOverride(override)
            .build()
    }

    private fun findLocalSubtitle(
        request: SubtitleRequest,
        priorities: List<String>,
        settings: AppSettings
    ): Pair<ActiveSubtitle, ExternalSubtitleFile>? {
        val locals = localScanner.scan(request.mediaUrl, request.title)
        if (locals.isEmpty()) return null
        val chosen = SubtitleLanguageResolver.pickBestLanguage(locals.map { it.language }, priorities)
            ?: return null
        val candidate = locals.firstOrNull {
            val code = SubtitleLanguageResolver.normalizeCode(it.language) ?: it.language
            code == SubtitleLanguageResolver.normalizeCode(chosen) || code.startsWith(chosen)
        } ?: locals.first()
        val file = SubtitleFileOffsetter.withDelay(candidate.file, settings.subtitleDelayMs, offsetCacheDir)
        return ActiveSubtitle(SubtitleSource.LOCAL, candidate.language, candidate.label) to
            ExternalSubtitleFile(file, candidate.language, candidate.mimeType)
    }

    private fun applyExternalSubtitle(
        player: ExoPlayer,
        mediaUrl: String,
        external: ExternalSubtitleFile,
        settings: AppSettings
    ) {
        val file = SubtitleFileOffsetter.withDelay(external.file, settings.subtitleDelayMs, offsetCacheDir)
        val positionMs = player.currentPosition
        val playWhenReady = player.playWhenReady
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(external.mimeType.ifBlank { MimeTypes.APPLICATION_SUBRIP })
            .setLanguage(external.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(mediaUrl)
                .setSubtitleConfigurations(listOf(config))
                .build(),
            positionMs
        )
        player.prepare()
        player.playWhenReady = playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(external.language)
            .build()
    }

    private fun disableTextTracks(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private suspend fun resolveImdbId(request: SubtitleRequest): String? {
        request.imdbId?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val key = request.providerKey ?: return null
        return titleEnrichmentRepository.getCached(key)?.imdbId
    }

    private fun prefetchOnline(request: SubtitleRequest, imdbId: String?, languages: List<String>) {
        scope.launch {
            subtitleRepository.prefetchInBackground(
                title = request.title,
                imdbId = imdbId,
                languages = languages,
                releaseYear = request.releaseYear
            )
        }
    }

    private data class EmbeddedTrack(
        val group: Tracks.Group,
        val index: Int,
        val format: Format
    )
}
