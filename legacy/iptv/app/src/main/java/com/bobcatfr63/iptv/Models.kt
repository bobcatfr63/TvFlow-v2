package com.bobcatfr63.iptv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    @SerialName("user_info") val userInfo: UserInfo? = null,
    @SerialName("server_info") val serverInfo: ServerInfo? = null
)

@Serializable
data class UserInfo(
    val username: String = "",
    val password: String = "",
    val message: String = "",
    val auth: Int = 0,
    val status: String = "",
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("active_cons") val activeConnections: String? = null
)

@Serializable
data class ServerInfo(
    val url: String = "",
    val port: String = "",
    @SerialName("https_port") val httpsPort: String = "",
    @SerialName("server_protocol") val protocol: String = "",
    val timezone: String = ""
)

@Serializable
data class Category(
    @SerialName("category_id") val id: String,
    @SerialName("category_name") val name: String,
    @SerialName("parent_id") val parentId: Int = 0
)

@Serializable
data class LiveStream(
    @SerialName("stream_id") val id: Int,
    val name: String,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("tv_archive") val tvArchive: Int = 0,
    @SerialName("tv_archive_duration") val tvArchiveDuration: Int = 0
)

@Serializable
data class VodStream(
    @SerialName("stream_id") val id: Int,
    val name: String,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("container_extension") val extension: String = "mp4",
    val rating: String? = null,
    @SerialName("rating_5based") val rating5: Double? = null,
    @SerialName("added") val added: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val year: String? = null
)

@Serializable
data class SeriesItem(
    @SerialName("series_id") val id: Int,
    val name: String,
    val cover: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    val plot: String? = null,
    val rating: String? = null,
    @SerialName("rating_5based") val rating5: Double? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val year: String? = null
)

@Serializable
data class SeriesInfoResponse(
    val info: SeriesInfo? = null,
    val episodes: Map<String, List<Episode>> = emptyMap()
)

@Serializable
data class SeriesInfo(
    val name: String = "",
    val cover: String? = null,
    val plot: String? = null
)

@Serializable
data class Episode(
    val id: String,
    @SerialName("episode_num") val episodeNumber: Int = 0,
    val title: String = "",
    @SerialName("container_extension") val extension: String = "mp4",
    val info: EpisodeInfo? = null
)

@Serializable
data class EpisodeInfo(
    val duration: String? = null,
    val plot: String? = null
)

data class EpgItem(
    val title: String,
    val description: String = "",
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L
)

data class Credentials(val server: String, val username: String, val password: String)

enum class ContentType { LIVE, MOVIE, SERIES_EPISODE }

data class Playable(
    val title: String,
    val url: String,
    val type: ContentType,
    val contentKey: String = "",
    val categoryId: String = "",
    val rating: String? = null
)
