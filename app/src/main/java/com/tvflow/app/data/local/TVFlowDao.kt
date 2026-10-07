package com.tvflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface TVFlowDao {
    @Query("SELECT * FROM live_categories WHERE profileId = :profileId AND isHidden = 0 ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, sortOrder, name")
    fun observeCategories(profileId: String): Flow<List<LiveCategoryEntity>>

    @Query("SELECT * FROM live_categories WHERE profileId = :profileId ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, sortOrder, name")
    fun observeManageCategories(profileId: String): Flow<List<LiveCategoryEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND categoryId = :categoryId AND isHidden = 0 ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, name")
    fun observeChannels(profileId: String, categoryId: String): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND categoryId IN (:categoryIds) AND isHidden = 0 ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, name")
    fun observeChannelsForCategories(profileId: String, categoryIds: List<String>): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, name")
    fun observeManageChannels(profileId: String, categoryId: String): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND isHidden = 0 AND categoryId NOT IN (SELECT id FROM live_categories WHERE profileId = :profileId AND (isHidden = 1 OR isLocked = 1)) ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, name")
    fun observeAllChannels(profileId: String): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND streamId = :streamId LIMIT 1")
    fun observeChannel(profileId: String, streamId: Int): Flow<LiveChannelEntity?>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND isFavorite = 1 AND isHidden = 0 AND categoryId NOT IN (SELECT id FROM live_categories WHERE profileId = :profileId AND (isHidden = 1 OR isLocked = 1)) ORDER BY CASE WHEN customOrder IS NULL THEN 1 ELSE 0 END, customOrder, name")
    fun observeFavorites(profileId: String): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND lastWatchedAt IS NOT NULL AND isHidden = 0 AND categoryId NOT IN (SELECT id FROM live_categories WHERE profileId = :profileId AND (isHidden = 1 OR isLocked = 1)) ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun observeRecentChannels(profileId: String, limit: Int): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND isHidden = 0 AND categoryId NOT IN (SELECT id FROM live_categories WHERE profileId = :profileId AND (isHidden = 1 OR isLocked = 1)) AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun searchLive(profileId: String, query: String, limit: Int = 100): Flow<List<LiveChannelEntity>>

    @Query("SELECT * FROM epg_programs WHERE profileId = :profileId AND streamId = :streamId ORDER BY startTimestamp")
    fun observeEpg(profileId: String, streamId: Int): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE profileId = :profileId AND streamId IN (:streamIds) ORDER BY streamId, startTimestamp")
    fun observeEpgForStreams(profileId: String, streamIds: List<Int>): Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpg(items: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun clearEpg(profileId: String, streamId: Int)

    @Query("DELETE FROM epg_programs WHERE profileId = :profileId AND stopTimestamp < :cutoff")
    suspend fun deleteExpiredEpg(profileId: String, cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<LiveCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(items: List<LiveChannelEntity>)

    @Query("UPDATE live_channels SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun toggleFavorite(profileId: String, streamId: Int)

    @Query("UPDATE live_channels SET lastWatchedAt = :watchedAt WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun markWatched(profileId: String, streamId: Int, watchedAt: Long)

    @Query("UPDATE live_channels SET isHidden = :hidden WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun setChannelHidden(profileId: String, streamId: Int, hidden: Boolean)

    @Query("UPDATE live_channels SET customOrder = :customOrder WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun setChannelOrder(profileId: String, streamId: Int, customOrder: Int)

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND streamId = :streamId LIMIT 1")
    suspend fun getChannel(profileId: String, streamId: Int): LiveChannelEntity?

    @Query("UPDATE live_channels SET healthStatus = :status, healthCheckedAt = :checkedAt, healthFailureCount = :failureCount, lastHttpCode = :httpCode, lastPlaybackError = :error WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun updateStreamHealth(profileId: String, streamId: Int, status: String, checkedAt: Long, failureCount: Int, httpCode: Int?, error: String?)

    @Query("UPDATE live_categories SET isHidden = :hidden WHERE profileId = :profileId AND id = :categoryId")
    suspend fun setCategoryHidden(profileId: String, categoryId: String, hidden: Boolean)

    @Query("UPDATE live_categories SET isLocked = :locked WHERE profileId = :profileId AND id = :categoryId")
    suspend fun setCategoryLocked(profileId: String, categoryId: String, locked: Boolean)

    @Query("UPDATE live_categories SET customOrder = :customOrder WHERE profileId = :profileId AND id = :categoryId")
    suspend fun setCategoryOrder(profileId: String, categoryId: String, customOrder: Int)

    @Query("DELETE FROM live_categories WHERE profileId = :profileId")
    suspend fun clearCategories(profileId: String)

    @Query("DELETE FROM live_channels WHERE profileId = :profileId")
    suspend fun clearChannels(profileId: String)

    @Query("SELECT streamId FROM live_channels WHERE profileId = :profileId AND isFavorite = 1")
    suspend fun getFavoriteIds(profileId: String): List<Int>

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId")
    suspend fun getAllChannels(profileId: String): List<LiveChannelEntity>

    @Query("UPDATE live_channels SET isFavorite = 0 WHERE profileId = :profileId")
    suspend fun clearFavorites(profileId: String)

    @Query("UPDATE live_channels SET isFavorite = 1 WHERE profileId = :profileId AND streamId IN (:streamIds)")
    suspend fun setFavoriteIds(profileId: String, streamIds: List<Int>)

    @Transaction
    suspend fun replaceFavorites(profileId: String, streamIds: List<Int>) {
        clearFavorites(profileId)
        if (streamIds.isNotEmpty()) setFavoriteIds(profileId, streamIds.distinct())
    }

    @Query("SELECT * FROM live_channels WHERE profileId = :profileId AND lastWatchedAt IS NOT NULL")
    suspend fun getWatched(profileId: String): List<LiveChannelEntity>

    @Query("SELECT streamId, isHidden, customOrder, healthStatus, healthCheckedAt, healthFailureCount, lastHttpCode, lastPlaybackError FROM live_channels WHERE profileId = :profileId")
    suspend fun getChannelPreferences(profileId: String): List<ChannelPreferenceRow>

    @Query("SELECT id, isHidden, isLocked, customOrder FROM live_categories WHERE profileId = :profileId")
    suspend fun getCategoryPreferences(profileId: String): List<CategoryPreferenceRow>

    @Transaction
    suspend fun replaceCatalog(profileId: String, categories: List<LiveCategoryEntity>, channels: List<LiveChannelEntity>) {
        val favorites = getFavoriteIds(profileId).toSet()
        val watched = getWatched(profileId).associate { it.streamId to it.lastWatchedAt }
        val channelPrefs = getChannelPreferences(profileId).associateBy { it.streamId }
        val categoryPrefs = getCategoryPreferences(profileId).associateBy { it.id }
        clearChannels(profileId)
        clearCategories(profileId)
        upsertCategories(categories.map { category ->
            val pref = categoryPrefs[category.id]
            category.copy(
                isHidden = pref?.isHidden ?: category.isHidden,
                isLocked = pref?.isLocked ?: category.isLocked,
                customOrder = pref?.customOrder ?: category.customOrder
            )
        })
        upsertChannels(channels.map { channel ->
            val pref = channelPrefs[channel.streamId]
            channel.copy(
                isFavorite = channel.streamId in favorites,
                lastWatchedAt = watched[channel.streamId],
                isHidden = pref?.isHidden ?: channel.isHidden,
                customOrder = pref?.customOrder ?: channel.customOrder,
                healthStatus = pref?.healthStatus ?: channel.healthStatus,
                healthCheckedAt = pref?.healthCheckedAt ?: channel.healthCheckedAt,
                healthFailureCount = pref?.healthFailureCount ?: channel.healthFailureCount,
                lastHttpCode = pref?.lastHttpCode ?: channel.lastHttpCode,
                lastPlaybackError = pref?.lastPlaybackError ?: channel.lastPlaybackError
            )
        })
    }

    @Query("SELECT * FROM media_categories WHERE profileId = :profileId AND type = :type ORDER BY sortOrder, name")
    fun observeMediaCategories(profileId: String, type: String): Flow<List<MediaCategoryEntity>>

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY name")
    fun observeVod(profileId: String, categoryId: String): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId ORDER BY COALESCE(added, 0) DESC, name LIMIT 1")
    suspend fun featuredVod(profileId: String): VodItemEntity?

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND categoryId IN (:categoryIds) ORDER BY COALESCE(added, 0) DESC, name LIMIT 1")
    suspend fun featuredVodForCategories(profileId: String, categoryIds: List<String>): VodItemEntity?

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND releaseYear IS NOT NULL ORDER BY releaseYear DESC, COALESCE(releaseMonth, 0) DESC, name LIMIT :limit")
    fun observeRecentVodReleases(profileId: String, limit: Int): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND categoryId IN (:categoryIds) ORDER BY CASE WHEN releaseYear IS NULL THEN 1 ELSE 0 END, releaseYear DESC, COALESCE(releaseMonth, 0) DESC, CAST(streamId AS INTEGER) DESC, name LIMIT :limit")
    fun observeRecentVodReleasesForCategories(profileId: String, categoryIds: List<String>, limit: Int): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM series_items WHERE profileId = :profileId AND releaseYear IS NOT NULL ORDER BY releaseYear DESC, COALESCE(releaseMonth, 0) DESC, name LIMIT :limit")
    fun observeRecentSeriesReleases(profileId: String, limit: Int): Flow<List<SeriesItemEntity>>

    @Query("SELECT * FROM series_items WHERE profileId = :profileId AND categoryId IN (:categoryIds) AND releaseYear IS NOT NULL ORDER BY releaseYear DESC, COALESCE(releaseMonth, 0) DESC, name LIMIT :limit")
    fun observeRecentSeriesReleasesForCategories(profileId: String, categoryIds: List<String>, limit: Int): Flow<List<SeriesItemEntity>>

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND categoryId = :categoryId AND (:year IS NULL OR releaseYear = :year) AND (:month IS NULL OR releaseMonth = :month) ORDER BY CASE WHEN releaseYear IS NULL THEN 1 ELSE 0 END, releaseYear DESC, releaseMonth DESC, name")
    fun pagingVod(profileId: String, categoryId: String, year: Int?, month: Int?): PagingSource<Int, VodItemEntity>

    @Query("SELECT DISTINCT releaseYear FROM vod_items WHERE profileId = :profileId AND categoryId = :categoryId AND releaseYear IS NOT NULL ORDER BY releaseYear DESC")
    fun observeVodReleaseYears(profileId: String, categoryId: String): Flow<List<Int>>

    @Query("SELECT DISTINCT releaseMonth FROM vod_items WHERE profileId = :profileId AND categoryId = :categoryId AND releaseYear = :year AND releaseMonth IS NOT NULL ORDER BY releaseMonth DESC")
    fun observeVodReleaseMonths(profileId: String, categoryId: String, year: Int): Flow<List<Int>>

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND categoryId = :categoryId AND (releaseYear IS NULL OR synopsis IS NULL OR synopsis = '') LIMIT :limit")
    suspend fun getVodMissingMetadata(profileId: String, categoryId: String, limit: Int): List<VodItemEntity>

    @Query("UPDATE vod_items SET synopsis = COALESCE(:synopsis, synopsis), releaseYear = COALESCE(:year, releaseYear), releaseMonth = COALESCE(:month, releaseMonth), posterUrl = COALESCE(:posterUrl, posterUrl), rating = COALESCE(:rating, rating) WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun updateVodMetadata(profileId: String, streamId: Int, synopsis: String?, year: Int?, month: Int?, posterUrl: String?, rating: String?)

    @Query("SELECT * FROM series_items WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY name")
    fun observeSeries(profileId: String, categoryId: String): Flow<List<SeriesItemEntity>>

    @Query("SELECT * FROM series_items WHERE profileId = :profileId AND categoryId = :categoryId AND (:year IS NULL OR releaseYear = :year) AND (:month IS NULL OR releaseMonth = :month) ORDER BY CASE WHEN releaseYear IS NULL THEN 1 ELSE 0 END, releaseYear DESC, releaseMonth DESC, name")
    fun pagingSeries(profileId: String, categoryId: String, year: Int?, month: Int?): PagingSource<Int, SeriesItemEntity>

    @Query("SELECT DISTINCT releaseYear FROM series_items WHERE profileId = :profileId AND categoryId = :categoryId AND releaseYear IS NOT NULL ORDER BY releaseYear DESC")
    fun observeSeriesReleaseYears(profileId: String, categoryId: String): Flow<List<Int>>

    @Query("SELECT DISTINCT releaseMonth FROM series_items WHERE profileId = :profileId AND categoryId = :categoryId AND releaseYear = :year AND releaseMonth IS NOT NULL ORDER BY releaseMonth DESC")
    fun observeSeriesReleaseMonths(profileId: String, categoryId: String, year: Int): Flow<List<Int>>

    @Query("SELECT * FROM series_items WHERE profileId = :profileId AND categoryId = :categoryId AND (releaseYear IS NULL OR plot IS NULL OR plot = '') LIMIT :limit")
    suspend fun getSeriesMissingMetadata(profileId: String, categoryId: String, limit: Int): List<SeriesItemEntity>

    @Query("UPDATE series_items SET plot = COALESCE(:plot, plot), releaseYear = COALESCE(:year, releaseYear), releaseMonth = COALESCE(:month, releaseMonth), coverUrl = COALESCE(:coverUrl, coverUrl), rating = COALESCE(:rating, rating) WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun updateSeriesMetadata(profileId: String, seriesId: Int, plot: String?, year: Int?, month: Int?, coverUrl: String?, rating: String?)

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun searchVod(profileId: String, query: String, limit: Int = 100): Flow<List<VodItemEntity>>

    @Query("SELECT * FROM series_items WHERE profileId = :profileId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun searchSeries(profileId: String, query: String, limit: Int = 100): Flow<List<SeriesItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaCategories(items: List<MediaCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVod(items: List<VodItemEntity>)

    @Query("SELECT * FROM vod_items WHERE profileId = :profileId")
    suspend fun getAllVod(profileId: String): List<VodItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(items: List<SeriesItemEntity>)

    @Query("SELECT * FROM series_items WHERE profileId = :profileId")
    suspend fun getAllSeries(profileId: String): List<SeriesItemEntity>

    @Query("DELETE FROM media_categories WHERE profileId = :profileId AND type = :type")
    suspend fun clearMediaCategories(profileId: String, type: String)

    @Query("DELETE FROM vod_items WHERE profileId = :profileId")
    suspend fun clearVod(profileId: String)

    @Query("DELETE FROM series_items WHERE profileId = :profileId")
    suspend fun clearSeries(profileId: String)

    @Transaction
    suspend fun replaceMediaCatalog(
        profileId: String,
        vodCategories: List<MediaCategoryEntity>,
        seriesCategories: List<MediaCategoryEntity>,
        vod: List<VodItemEntity>,
        series: List<SeriesItemEntity>
    ) {
        val previousVod = getAllVod(profileId).associateBy { it.streamId }
        val previousSeries = getAllSeries(profileId).associateBy { it.seriesId }

        clearMediaCategories(profileId, "VOD")
        clearMediaCategories(profileId, "SERIES")
        clearVod(profileId)
        clearSeries(profileId)
        upsertMediaCategories(vodCategories)
        upsertMediaCategories(seriesCategories)

        vod.map { incoming ->
            val previous = previousVod[incoming.streamId]
            incoming.copy(
                synopsis = incoming.synopsis ?: previous?.synopsis,
                releaseYear = incoming.releaseYear ?: previous?.releaseYear,
                releaseMonth = incoming.releaseMonth ?: previous?.releaseMonth,
                posterUrl = incoming.posterUrl ?: previous?.posterUrl,
                rating = incoming.rating ?: previous?.rating
            )
        }.chunked(500).forEach { batch -> upsertVod(batch) }

        series.map { incoming ->
            val previous = previousSeries[incoming.seriesId]
            incoming.copy(
                plot = incoming.plot ?: previous?.plot,
                releaseYear = incoming.releaseYear ?: previous?.releaseYear,
                releaseMonth = incoming.releaseMonth ?: previous?.releaseMonth,
                coverUrl = incoming.coverUrl ?: previous?.coverUrl,
                rating = incoming.rating ?: previous?.rating
            )
        }.chunked(500).forEach { batch -> upsertSeries(batch) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackPosition(item: PlaybackPositionEntity)

    @Query("SELECT * FROM playback_positions WHERE profileId = :profileId AND mediaKey = :mediaKey LIMIT 1")
    suspend fun getPlaybackPosition(profileId: String, mediaKey: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    fun observePlaybackPositions(profileId: String, limit: Int = 20): Flow<List<PlaybackPositionEntity>>

    @Query("DELETE FROM playback_positions WHERE profileId = :profileId AND mediaKey = :mediaKey")
    suspend fun clearPlaybackPosition(profileId: String, mediaKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(item: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY watchedAt DESC LIMIT :limit")
    fun observeWatchHistory(profileId: String, limit: Int = 40): Flow<List<WatchHistoryEntity>>

    @Query("SELECT mediaId FROM watch_history WHERE profileId = :profileId AND kind = :kind AND completed = 1")
    fun observeCompletedMediaIds(profileId: String, kind: String): Flow<List<String>>

    @Query("SELECT mediaId FROM watch_history WHERE profileId = :profileId AND kind = 'episode' AND seriesId = :seriesId AND completed = 1")
    fun observeCompletedEpisodeIds(profileId: String, seriesId: Int): Flow<List<String>>

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clearWatchHistory(profileId: String)

    @Query("DELETE FROM epg_programs WHERE profileId = :profileId")
    suspend fun clearAllEpg(profileId: String)

    @Query("DELETE FROM playback_positions WHERE profileId = :profileId")
    suspend fun clearPlaybackPositions(profileId: String)

    @Query("DELETE FROM media_categories WHERE profileId = :profileId")
    suspend fun clearAllMediaCategories(profileId: String)

    @Transaction
    suspend fun deleteProfileData(profileId: String) {
        clearAllEpg(profileId)
        clearChannels(profileId)
        clearCategories(profileId)
        clearVod(profileId)
        clearSeries(profileId)
        clearAllMediaCategories(profileId)
        clearPlaybackPositions(profileId)
        clearWatchHistory(profileId)
    }
}

data class ChannelPreferenceRow(
    val streamId: Int,
    val isHidden: Boolean,
    val customOrder: Int?,
    val healthStatus: String,
    val healthCheckedAt: Long?,
    val healthFailureCount: Int,
    val lastHttpCode: Int?,
    val lastPlaybackError: String?
)
data class CategoryPreferenceRow(val id: String, val isHidden: Boolean, val isLocked: Boolean, val customOrder: Int?)
