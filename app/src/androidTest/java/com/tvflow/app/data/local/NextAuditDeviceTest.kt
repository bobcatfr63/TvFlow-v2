package com.tvflow.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tvflow.app.BuildConfig
import com.tvflow.app.data.metadata.TmdbMetadataClient
import com.tvflow.app.di.AppModule
import com.tvflow.app.domain.MediaType
import com.tvflow.app.ui.search.AiUsageCounter
import com.tvflow.app.ui.search.GroqIntentClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in audit of the configured Next profile. No login, refresh, restore or credential writes. */
@RunWith(AndroidJUnit4::class)
class NextAuditDeviceTest {
    @Test fun inspectConfiguredServicesWithoutReplacingUserData() = runBlocking {
        assertEquals("Audit must target Next", "com.tvflow.app.next", BuildConfig.APPLICATION_ID)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val creds = CredentialsStore(context).credentials.first()
        val client = AppModule.provideOkHttp()
        val api = AppModule.provideXtreamApi(client)
        suspend fun probe(name: String, block: suspend () -> String) {
            val start = SystemClock.elapsedRealtime()
            val outcome = try { block() } catch (e: Exception) {
                // Throwable messages can contain IPTV URLs: log only the class.
                "ERROR:" + e.javaClass.simpleName
            }
            Log.i("NextAudit", "$name=$outcome elapsedMs=${SystemClock.elapsedRealtime()-start}")
        }
        if (creds != null && creds.username != "__tvflow_m3u__") {
            val endpoint = creds.serverUrl.trimEnd('/').let {
                if (it.endsWith("player_api.php")) it else "$it/player_api.php"
            }
            probe("xtreamAuth") { api.authenticate(endpoint, creds.username, creds.password); "RESPONSE_PARSED" }
            probe("liveCategories") { "count=" + api.getLiveCategories(endpoint, creds.username, creds.password).size }
            probe("vodCategories") { "count=" + api.getVodCategories(endpoint, creds.username, creds.password).size }
            probe("seriesCategories") { "count=" + api.getSeriesCategories(endpoint, creds.username, creds.password).size }
            val dbFile = context.getDatabasePath("tvflow.db")
            if (dbFile.isFile) SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                for (table in listOf("live_channels", "vod_items", "series_items", "epg_programs")) {
                    db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                        c.moveToFirst(); Log.i("NextAudit", "$table cachedRows=${c.getLong(0)}")
                    }
                }
                db.rawQuery("SELECT streamId FROM live_channels LIMIT 1", null).use { c ->
                    if (c.moveToFirst()) probe("epgSample") {
                        api.getShortEpg(endpoint, creds.username, creds.password, streamId=c.getInt(0)); "RESPONSE_PARSED"
                    }
                }
                db.rawQuery("SELECT seriesId FROM series_items LIMIT 1", null).use { c ->
                    if (c.moveToFirst()) probe("episodesSample") {
                        "seasons=" + api.getSeriesInfo(endpoint, creds.username, creds.password, seriesId=c.getInt(0)).episodes.orEmpty().size
                    }
                }
                db.rawQuery("EXPLAIN QUERY PLAN SELECT * FROM vod_items WHERE profileId=? AND categoryId=? ORDER BY name COLLATE NOCASE LIMIT 60", arrayOf("audit", "audit")).use { c ->
                    while (c.moveToNext()) Log.i("NextAudit", "vodQueryPlan="+c.getString(3))
                }
            }
        } else Log.i("NextAudit", "xtream=NO_CONFIGURED_XTREAM_PROFILE")
        probe("tmdb") {
            val metadata = TmdbMetadataClient(client, SettingsStore(context), context).search(MediaType.VOD, "Inception", 2010)
            if (metadata == null) "NO_MATCH_OR_UNAVAILABLE" else "MATCH backdrop="+!metadata.backdropUrl.isNullOrBlank()
        }
        probe("groq") {
            val hint = GroqIntentClient(context, AiUsageCounter(context)).rewrite("une comédie française")
            if (hint.isNullOrBlank()) "NO_RESPONSE_OR_NOT_CONFIGURED" else "RESPONSE_RECEIVED"
        }
    }
}
