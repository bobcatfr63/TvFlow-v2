package com.tvflow.app.data.remote

import com.google.gson.annotations.SerializedName

data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
) {
    data class UserInfo(
        val username: String? = null,
        val password: String? = null,
        val message: String? = null,
        val auth: Int? = null,
        val status: String? = null,
        @SerializedName("exp_date") val expiryDate: String? = null,
        @SerializedName("is_trial") val isTrial: String? = null,
        @SerializedName("active_cons") val activeConnections: String? = null,
        @SerializedName("max_connections") val maxConnections: String? = null
    )

    data class ServerInfo(
        val url: String? = null,
        val port: String? = null,
        val protocol: String? = null,
        val timezone: String? = null,
        @SerializedName("timestamp_now") val timestampNow: Long? = null
    )
}

data class LiveCategoryDto(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int? = null
)

data class LiveStreamDto(
    @SerializedName("num") val number: Int? = null,
    val name: String,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int? = null,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int? = null
)


data class ShortEpgResponse(
    @SerializedName("epg_listings") val listings: List<EpgListingDto> = emptyList()
)

data class EpgListingDto(
    val id: String? = null,
    @SerializedName("epg_id") val epgId: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: String? = null,
    @SerializedName("stop_timestamp") val stopTimestamp: String? = null,
    @SerializedName("has_archive") val hasArchive: Int? = null
)


data class MediaCategoryDto(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int? = null
)

data class VodStreamDto(
    @SerializedName("stream_id") val streamId: Int,
    val name: String,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    val rating: String? = null,
    val added: String? = null,
    @SerializedName(value = "releaseDate", alternate = ["release_date", "releasedate"]) val releaseDate: String? = null,
    @SerializedName(value = "year", alternate = ["releaseYear", "release_year"]) val year: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null
)

data class SeriesDto(
    @SerializedName("series_id") val seriesId: Int,
    val name: String,
    val cover: String? = null,
    val plot: String? = null,
    val rating: String? = null,
    @SerializedName(value = "releaseDate", alternate = ["release_date", "releasedate"]) val releaseDate: String? = null,
    @SerializedName(value = "year", alternate = ["releaseYear", "release_year"]) val year: String? = null,
    @SerializedName("category_id") val categoryId: String? = null
)

data class SeriesInfoResponse(
    val info: SeriesInfoDto? = null,
    val episodes: Map<String, List<EpisodeDto>> = emptyMap()
)

data class SeriesInfoDto(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val rating: String? = null
)

data class EpisodeDto(
    val id: String,
    @SerializedName("episode_num") val episodeNumber: Int? = null,
    val title: String? = null,
    @SerializedName("container_extension") val containerExtension: String? = null,
    val info: EpisodeInfoDto? = null
)

data class EpisodeInfoDto(
    val plot: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    val duration: String? = null
)
