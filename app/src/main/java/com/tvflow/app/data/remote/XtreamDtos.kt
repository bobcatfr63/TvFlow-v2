package com.tvflow.app.data.remote

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

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

data class VodInfoResponse(
    @JsonAdapter(FlexibleVodInfoAdapter::class) val info: VodInfoDto? = null,
    @SerializedName("movie_data") val movieData: VodMovieDataDto? = null
)

data class VodInfoDto(
    @SerializedName("trailer") val trailer: String? = null,
    @SerializedName(value = "movie_image", alternate = ["cover_big", "stream_icon"]) val movieImage: String? = null,
    @SerializedName(value = "plot", alternate = ["description", "overview"]) val plot: String? = null,
    val rating: String? = null,
    @SerializedName(value = "releasedate", alternate = ["releaseDate", "release_date"]) val releaseDate: String? = null,
    @SerializedName(value = "year", alternate = ["releaseYear", "release_year"]) val year: String? = null
)

/** Providers may return `info` as an object, an array or an empty array. */
class FlexibleVodInfoAdapter : JsonDeserializer<VodInfoDto> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): VodInfoDto? {
        val info = when {
            json == null || json.isJsonNull -> null
            json.isJsonObject -> json
            json.isJsonArray -> json.asJsonArray.firstOrNull { it.isJsonObject }
            else -> null
        } ?: return null
        return context?.deserialize(info, VodInfoDto::class.java)
    }
}

data class VodMovieDataDto(
    @SerializedName("stream_id") val streamId: Int? = null,
    val name: String? = null,
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
    @JsonAdapter(FlexibleSeriesInfoAdapter::class)
    val info: SeriesInfoDto? = null,
    @JsonAdapter(FlexibleSeriesEpisodesAdapter::class)
    val episodes: Map<String, List<EpisodeDto>> = emptyMap()
)

data class SeriesInfoDto(
    @SerializedName("trailer") val trailer: String? = null,
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
    val info: JsonElement? = null
)

data class EpisodeInfoDto(
    val plot: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    val duration: String? = null
)

class FlexibleSeriesInfoAdapter : JsonDeserializer<SeriesInfoDto> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): SeriesInfoDto? {
        val value = when {
            json == null || json.isJsonNull -> null
            json.isJsonObject -> json
            json.isJsonArray -> json.asJsonArray.firstOrNull { it.isJsonObject }
            else -> null
        } ?: return null
        return context?.deserialize(value, SeriesInfoDto::class.java)
    }
}

class FlexibleSeriesEpisodesAdapter : JsonDeserializer<Map<String, List<EpisodeDto>>> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Map<String, List<EpisodeDto>> {
        if (json == null || !json.isJsonObject) return emptyMap()
        return buildMap {
            for ((season, raw) in json.asJsonObject.entrySet()) {
                if (!raw.isJsonArray) continue
                val valid = raw.asJsonArray.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    runCatching {
                        context?.deserialize<EpisodeDto>(element, EpisodeDto::class.java)
                            ?.takeIf { it.id.isNotBlank() }
                    }.getOrNull()
                }
                put(season, valid)
            }
        }
    }
}
