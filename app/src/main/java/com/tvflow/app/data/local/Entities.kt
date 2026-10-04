package com.tvflow.app.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "live_categories",
    primaryKeys = ["profileId", "id"],
    indices = [Index("profileId"), Index("name")]
)
data class LiveCategoryEntity(
    val profileId: String,
    val id: String,
    val name: String,
    val sortOrder: Int,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val customOrder: Int? = null
)

@Entity(
    tableName = "live_channels",
    primaryKeys = ["profileId", "streamId"],
    indices = [
        Index("profileId"), Index("categoryId"), Index("name"),
        Index("isFavorite"), Index("lastWatchedAt")
    ]
)
data class LiveChannelEntity(
    val profileId: String,
    val streamId: Int,
    val name: String,
    val categoryId: String,
    val logoUrl: String?,
    val epgChannelId: String?,
    val streamType: String,
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
    val isHidden: Boolean = false,
    val customOrder: Int? = null,
    val healthStatus: String = "UNKNOWN",
    val healthCheckedAt: Long? = null,
    val healthFailureCount: Int = 0,
    val lastHttpCode: Int? = null,
    val lastPlaybackError: String? = null,
    val tvArchive: Boolean = false,
    val tvArchiveDuration: Int = 0
)

@Entity(
    tableName = "epg_programs",
    primaryKeys = ["profileId", "streamId", "startTimestamp"],
    indices = [Index("profileId"), Index("streamId"), Index("startTimestamp"), Index("stopTimestamp")]
)
data class EpgProgramEntity(
    val profileId: String,
    val streamId: Int,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val title: String,
    val description: String?,
    val hasArchive: Boolean = false
)
