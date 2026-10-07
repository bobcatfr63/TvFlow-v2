package com.tvflow.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "media_categories",
    primaryKeys = ["profileId", "id", "type"],
    indices = [Index("profileId"), Index("type"), Index("name")]
)
data class MediaCategoryEntity(
    val profileId: String,
    val id: String,
    val name: String,
    val type: String,
    val sortOrder: Int
)

@Entity(
    tableName = "vod_items",
    primaryKeys = ["profileId", "streamId"],
    indices = [Index("profileId"), Index("categoryId"), Index("name")]
)
data class VodItemEntity(
    val profileId: String,
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val posterUrl: String?,
    val rating: String?,
    val added: Long?,
    val synopsis: String? = null,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val containerExtension: String?
)

@Entity(
    tableName = "series_items",
    primaryKeys = ["profileId", "seriesId"],
    indices = [Index("profileId"), Index("categoryId"), Index("name")]
)
data class SeriesItemEntity(
    val profileId: String,
    val seriesId: Int,
    val name: String,
    val categoryId: String,
    val coverUrl: String?,
    val rating: String?,
    val plot: String?,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null
)

@Entity(
    tableName = "playback_positions",
    primaryKeys = ["profileId", "mediaKey"],
    indices = [Index("profileId"), Index("updatedAt")]
)
data class PlaybackPositionEntity(
    val profileId: String,
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
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["profileId", "mediaKey"],
    indices = [Index("profileId"), Index("watchedAt"), Index("kind"), Index("completed")]
)
data class WatchHistoryEntity(
    val profileId: String,
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
