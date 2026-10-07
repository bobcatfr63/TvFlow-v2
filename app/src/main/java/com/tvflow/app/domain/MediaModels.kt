package com.tvflow.app.domain

enum class MediaType { VOD, SERIES }

enum class MediaSection { FRANCE, KIDS, OTHER }

data class MediaCategory(
    val id: String,
    val name: String,
    val type: MediaType,
    val order: Int = 0,
    val section: MediaSection = MediaSection.OTHER
)

data class VodItem(
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val posterUrl: String?,
    val rating: String?,
    val added: Long?,
    val containerExtension: String?,
    val synopsis: String? = null,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null
)

data class SeriesItem(
    val seriesId: Int,
    val name: String,
    val categoryId: String,
    val coverUrl: String?,
    val rating: String?,
    val plot: String?,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null
)

data class Episode(
    val id: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String?,
    val imageUrl: String?,
    val plot: String?
)

data class PlaybackPosition(
    val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String,
    val imageUrl: String?,
    val kind: String,
    val mediaId: String,
    val containerExtension: String?,
    val seriesId: Int? = null
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class WatchHistoryItem(
    val mediaKey: String,
    val kind: String,
    val mediaId: String,
    val seriesId: Int?,
    val title: String,
    val imageUrl: String?,
    val containerExtension: String?,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
    val completed: Boolean
)
