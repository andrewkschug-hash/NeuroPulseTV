package com.grid.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * On-device cache of TMDB metadata fetched lazily when the user browses a title.
 * Supabase is not used for title metadata — only local Room + on-demand TMDB calls.
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

