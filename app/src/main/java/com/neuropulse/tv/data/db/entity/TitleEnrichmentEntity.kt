package com.neuropulse.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Enriched metadata for a provider title (VOD movie or series episode/show).
 *
 * For now this is stored locally and can be populated by an in-app enrichment step.
 * When a real backend exists, the client can treat this as a cache of server-provided results.
 */
@Entity(
    tableName = "title_enrichment",
    primaryKeys = ["providerKey"],
    indices = [
        Index("tmdbId"),
        Index("normalizedTitle")
    ]
)
data class TitleEnrichmentEntity(
    /**
     * Stable key for the provider item, e.g.
     * - "xtream:vod:<playlistId>:<streamId>"
     * - "xtream:series:<playlistId>:<seriesId>"
     * - "m3u:vod:<playlistId>:<id>"
     */
    val providerKey: String,
    val normalizedTitle: String,
    val releaseYear: Int? = null,

    val tmdbId: Long? = null,
    val imdbId: String? = null,
    val mediaType: String? = null,

    val title: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val cast: String? = null,
    val directors: String? = null,
    val writers: String? = null,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val popularity: Double? = null,

    val posterUrl: String? = null,
    val backdropUrl: String? = null,

    /** Comma-separated canonical genres (server taxonomy can later replace). */
    val genres: String? = null,
    /** Comma-separated keywords (server taxonomy can later replace). */
    val keywords: String? = null,
    val spokenLanguages: String? = null,
    val originCountry: String? = null,
    val status: String? = null,
    val ageCertification: String? = null,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val episodeRunTime: String? = null,

    /** 68 comma-separated floats. */
    val contentVector: String? = null,

    val updatedAt: Long = System.currentTimeMillis()
)

