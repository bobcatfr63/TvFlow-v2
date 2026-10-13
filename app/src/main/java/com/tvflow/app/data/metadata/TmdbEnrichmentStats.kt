package com.tvflow.app.data.metadata

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class TmdbEnrichmentStats(
    val attempted: Int = 0,
    val enriched: Int = 0,
    val failed: Int = 0,
    val lastAttemptAt: Long = 0L
)

/** Counts actual TMDB lookup outcomes, scoped to each Xtream profile and persisted across restarts. */
@Singleton
class TmdbEnrichmentTracker @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("tvflow_tmdb_enrichment_stats", Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    private fun key(profileId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(profileId.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun snapshot(profileId: String?): TmdbEnrichmentStats {
        if (profileId.isNullOrBlank()) return TmdbEnrichmentStats()
        val prefix = key(profileId)
        return TmdbEnrichmentStats(
            attempted = prefs.getInt("${prefix}_attempted", 0),
            enriched = prefs.getInt("${prefix}_enriched", 0),
            failed = prefs.getInt("${prefix}_failed", 0),
            lastAttemptAt = prefs.getLong("${prefix}_last", 0L)
        )
    }

    @Synchronized
    fun record(profileId: String, enriched: Boolean) {
        val current = snapshot(profileId)
        val prefix = key(profileId)
        prefs.edit()
            .putInt("${prefix}_attempted", current.attempted + 1)
            .putInt("${prefix}_enriched", current.enriched + if (enriched) 1 else 0)
            .putInt("${prefix}_failed", current.failed + if (enriched) 0 else 1)
            .putLong("${prefix}_last", System.currentTimeMillis())
            .apply()
        _revision.value = _revision.value + 1L
    }
}
