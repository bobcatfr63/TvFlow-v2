package com.tvflow.app.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tvflow.app.BuildConfig
import com.tvflow.app.data.local.TVFlowDao
import com.tvflow.app.data.local.TVFlowDatabase
import com.tvflow.app.data.remote.XtreamApi
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE live_channels ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE live_channels ADD COLUMN lastWatchedAt INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_live_channels_isFavorite ON live_channels(isFavorite)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_live_channels_lastWatchedAt ON live_channels(lastWatchedAt)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS epg_programs (streamId INTEGER NOT NULL, startTimestamp INTEGER NOT NULL, stopTimestamp INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, hasArchive INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(streamId, startTimestamp))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_streamId ON epg_programs(streamId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_startTimestamp ON epg_programs(startTimestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_stopTimestamp ON epg_programs(stopTimestamp)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS media_categories (id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(id, type))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_categories_type ON media_categories(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_media_categories_name ON media_categories(name)")

            db.execSQL("CREATE TABLE IF NOT EXISTS vod_items (streamId INTEGER NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, posterUrl TEXT, rating TEXT, added INTEGER, containerExtension TEXT, PRIMARY KEY(streamId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_items_categoryId ON vod_items(categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_items_name ON vod_items(name)")

            db.execSQL("CREATE TABLE IF NOT EXISTS series_items (seriesId INTEGER NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, coverUrl TEXT, rating TEXT, plot TEXT, PRIMARY KEY(seriesId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_series_items_categoryId ON series_items(categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_series_items_name ON series_items(name)")

            db.execSQL("CREATE TABLE IF NOT EXISTS playback_positions (mediaKey TEXT NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(mediaKey))")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN title TEXT NOT NULL DEFAULT 'Lecture'")
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN imageUrl TEXT")
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN kind TEXT NOT NULL DEFAULT 'vod'")
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN mediaId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN containerExtension TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE live_categories RENAME TO live_categories_old")
            db.execSQL("CREATE TABLE live_categories (profileId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, isHidden INTEGER NOT NULL, isLocked INTEGER NOT NULL, customOrder INTEGER, PRIMARY KEY(profileId, id))")
            db.execSQL("INSERT INTO live_categories(profileId,id,name,sortOrder,isHidden,isLocked,customOrder) SELECT 'legacy',id,name,sortOrder,0,0,NULL FROM live_categories_old")
            db.execSQL("DROP TABLE live_categories_old")
            db.execSQL("CREATE INDEX index_live_categories_profileId ON live_categories(profileId)")
            db.execSQL("CREATE INDEX index_live_categories_name ON live_categories(name)")

            db.execSQL("ALTER TABLE live_channels RENAME TO live_channels_old")
            db.execSQL("CREATE TABLE live_channels (profileId TEXT NOT NULL, streamId INTEGER NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, logoUrl TEXT, epgChannelId TEXT, streamType TEXT NOT NULL, isFavorite INTEGER NOT NULL, lastWatchedAt INTEGER, isHidden INTEGER NOT NULL, customOrder INTEGER, PRIMARY KEY(profileId, streamId))")
            db.execSQL("INSERT INTO live_channels(profileId,streamId,name,categoryId,logoUrl,epgChannelId,streamType,isFavorite,lastWatchedAt,isHidden,customOrder) SELECT 'legacy',streamId,name,categoryId,logoUrl,epgChannelId,streamType,isFavorite,lastWatchedAt,0,NULL FROM live_channels_old")
            db.execSQL("DROP TABLE live_channels_old")
            db.execSQL("CREATE INDEX index_live_channels_profileId ON live_channels(profileId)")
            db.execSQL("CREATE INDEX index_live_channels_categoryId ON live_channels(categoryId)")
            db.execSQL("CREATE INDEX index_live_channels_name ON live_channels(name)")
            db.execSQL("CREATE INDEX index_live_channels_isFavorite ON live_channels(isFavorite)")
            db.execSQL("CREATE INDEX index_live_channels_lastWatchedAt ON live_channels(lastWatchedAt)")

            db.execSQL("ALTER TABLE epg_programs RENAME TO epg_programs_old")
            db.execSQL("CREATE TABLE epg_programs (profileId TEXT NOT NULL, streamId INTEGER NOT NULL, startTimestamp INTEGER NOT NULL, stopTimestamp INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, hasArchive INTEGER NOT NULL, PRIMARY KEY(profileId, streamId, startTimestamp))")
            db.execSQL("INSERT INTO epg_programs(profileId,streamId,startTimestamp,stopTimestamp,title,description,hasArchive) SELECT 'legacy',streamId,startTimestamp,stopTimestamp,title,description,hasArchive FROM epg_programs_old")
            db.execSQL("DROP TABLE epg_programs_old")
            db.execSQL("CREATE INDEX index_epg_programs_profileId ON epg_programs(profileId)")
            db.execSQL("CREATE INDEX index_epg_programs_streamId ON epg_programs(streamId)")
            db.execSQL("CREATE INDEX index_epg_programs_startTimestamp ON epg_programs(startTimestamp)")
            db.execSQL("CREATE INDEX index_epg_programs_stopTimestamp ON epg_programs(stopTimestamp)")

            db.execSQL("ALTER TABLE media_categories RENAME TO media_categories_old")
            db.execSQL("CREATE TABLE media_categories (profileId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, sortOrder INTEGER NOT NULL, PRIMARY KEY(profileId,id,type))")
            db.execSQL("INSERT INTO media_categories(profileId,id,name,type,sortOrder) SELECT 'legacy',id,name,type,sortOrder FROM media_categories_old")
            db.execSQL("DROP TABLE media_categories_old")
            db.execSQL("CREATE INDEX index_media_categories_profileId ON media_categories(profileId)")
            db.execSQL("CREATE INDEX index_media_categories_type ON media_categories(type)")
            db.execSQL("CREATE INDEX index_media_categories_name ON media_categories(name)")

            db.execSQL("ALTER TABLE vod_items RENAME TO vod_items_old")
            db.execSQL("CREATE TABLE vod_items (profileId TEXT NOT NULL, streamId INTEGER NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, posterUrl TEXT, rating TEXT, added INTEGER, containerExtension TEXT, PRIMARY KEY(profileId,streamId))")
            db.execSQL("INSERT INTO vod_items(profileId,streamId,name,categoryId,posterUrl,rating,added,containerExtension) SELECT 'legacy',streamId,name,categoryId,posterUrl,rating,added,containerExtension FROM vod_items_old")
            db.execSQL("DROP TABLE vod_items_old")
            db.execSQL("CREATE INDEX index_vod_items_profileId ON vod_items(profileId)")
            db.execSQL("CREATE INDEX index_vod_items_categoryId ON vod_items(categoryId)")
            db.execSQL("CREATE INDEX index_vod_items_name ON vod_items(name)")

            db.execSQL("ALTER TABLE series_items RENAME TO series_items_old")
            db.execSQL("CREATE TABLE series_items (profileId TEXT NOT NULL, seriesId INTEGER NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, coverUrl TEXT, rating TEXT, plot TEXT, PRIMARY KEY(profileId,seriesId))")
            db.execSQL("INSERT INTO series_items(profileId,seriesId,name,categoryId,coverUrl,rating,plot) SELECT 'legacy',seriesId,name,categoryId,coverUrl,rating,plot FROM series_items_old")
            db.execSQL("DROP TABLE series_items_old")
            db.execSQL("CREATE INDEX index_series_items_profileId ON series_items(profileId)")
            db.execSQL("CREATE INDEX index_series_items_categoryId ON series_items(categoryId)")
            db.execSQL("CREATE INDEX index_series_items_name ON series_items(name)")

            db.execSQL("ALTER TABLE playback_positions RENAME TO playback_positions_old")
            db.execSQL("CREATE TABLE playback_positions (profileId TEXT NOT NULL, mediaKey TEXT NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL, title TEXT NOT NULL, imageUrl TEXT, kind TEXT NOT NULL, mediaId TEXT NOT NULL, containerExtension TEXT, PRIMARY KEY(profileId,mediaKey))")
            db.execSQL("INSERT INTO playback_positions(profileId,mediaKey,positionMs,durationMs,updatedAt,title,imageUrl,kind,mediaId,containerExtension) SELECT 'legacy',mediaKey,positionMs,durationMs,updatedAt,title,imageUrl,kind,mediaId,containerExtension FROM playback_positions_old")
            db.execSQL("DROP TABLE playback_positions_old")
            db.execSQL("CREATE INDEX index_playback_positions_profileId ON playback_positions(profileId)")
            db.execSQL("CREATE INDEX index_playback_positions_updatedAt ON playback_positions(updatedAt)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE live_channels ADD COLUMN healthStatus TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE live_channels ADD COLUMN healthCheckedAt INTEGER")
            db.execSQL("ALTER TABLE live_channels ADD COLUMN healthFailureCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE live_channels ADD COLUMN lastHttpCode INTEGER")
            db.execSQL("ALTER TABLE live_channels ADD COLUMN lastPlaybackError TEXT")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE live_channels ADD COLUMN tvArchive INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE live_channels ADD COLUMN tvArchiveDuration INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vod_items ADD COLUMN synopsis TEXT")
            db.execSQL("ALTER TABLE vod_items ADD COLUMN releaseYear INTEGER")
            db.execSQL("ALTER TABLE vod_items ADD COLUMN releaseMonth INTEGER")
            db.execSQL("ALTER TABLE series_items ADD COLUMN releaseYear INTEGER")
            db.execSQL("ALTER TABLE series_items ADD COLUMN releaseMonth INTEGER")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN seriesId INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS watch_history (" +
                    "profileId TEXT NOT NULL, mediaKey TEXT NOT NULL, kind TEXT NOT NULL, mediaId TEXT NOT NULL, " +
                    "seriesId INTEGER, title TEXT NOT NULL, imageUrl TEXT, containerExtension TEXT, " +
                    "positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, watchedAt INTEGER NOT NULL, " +
                    "completed INTEGER NOT NULL, PRIMARY KEY(profileId, mediaKey))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_profileId ON watch_history(profileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_watchedAt ON watch_history(watchedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_kind ON watch_history(kind)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_completed ON watch_history(completed)")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vod_items ADD COLUMN detailsFetchedAt INTEGER")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE vod_items ADD COLUMN tmdbGenreIds TEXT")
            db.execSQL("ALTER TABLE series_items ADD COLUMN tmdbGenreIds TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TVFlowDatabase =
        Room.databaseBuilder(context, TVFlowDatabase::class.java, "tvflow.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .build()

    @Provides fun provideDao(db: TVFlowDatabase): TVFlowDao = db.dao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", "VLC/3.0.21 LibVLC/3.0.21")
                    .header("Accept", "*/*")
                    .build()
                if (BuildConfig.DEBUG) {
                    val url = request.url
                    Log.d("TVFlowHTTP", "${request.method} ${url.scheme}://${url.host}:${url.port}")
                }
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideXtreamApi(client: OkHttpClient): XtreamApi = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(XtreamApi::class.java)
}
