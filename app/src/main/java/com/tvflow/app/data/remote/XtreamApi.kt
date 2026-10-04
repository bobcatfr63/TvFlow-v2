package com.tvflow.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApi {
    @GET
    suspend fun authenticate(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAuthResponse

    @GET
    suspend fun getLiveCategories(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<LiveCategoryDto>

    @GET
    suspend fun getShortEpg(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: Int,
        @Query("limit") limit: Int = 8
    ): ShortEpgResponse

    @GET
    suspend fun getVodCategories(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<MediaCategoryDto>

    @GET
    suspend fun getSeriesCategories(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): List<MediaCategoryDto>

    @GET
    suspend fun getVodStreams(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): List<VodStreamDto>

    @GET
    suspend fun getSeries(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): List<SeriesDto>

    @GET
    suspend fun getSeriesInfo(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): SeriesInfoResponse

    @GET
    suspend fun getLiveStreams(
        @Url playerApiUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): List<LiveStreamDto>
}
