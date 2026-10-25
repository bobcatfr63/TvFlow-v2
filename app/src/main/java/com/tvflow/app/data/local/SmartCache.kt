package com.tvflow.app.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded, profile/category-scoped display snapshots. Room remains authoritative.
 * Does not store credentials or media streams.
 */
@Singleton
class SmartCache @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("tvflow_smart_cache_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val vodType = object : TypeToken<List<VodItem>>() {}.type
    private val seriesType = object : TypeToken<List<SeriesItem>>() {}.type

    private fun key(profile: String, kind: String, categories: List<String>): String {
        val signature = "$profile|$kind|" + categories.sorted().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    fun loadVod(profile: String, categories: List<String>): List<VodItem> = runCatching {
        gson.fromJson<List<VodItem>>(prefs.getString(key(profile, "vod", categories), null), vodType)
            .orEmpty().filter { it.categoryId in categories }.take(36)
    }.getOrDefault(emptyList())

    fun loadSeries(profile: String, categories: List<String>): List<SeriesItem> = runCatching {
        gson.fromJson<List<SeriesItem>>(prefs.getString(key(profile, "series", categories), null), seriesType)
            .orEmpty().filter { it.categoryId in categories }.take(36)
    }.getOrDefault(emptyList())

    fun saveVod(profile: String, categories: List<String>, items: List<VodItem>) {
        if (items.isNotEmpty()) prefs.edit()
            .putString(key(profile, "vod", categories), gson.toJson(items.take(36))).apply()
    }

    fun saveSeries(profile: String, categories: List<String>, items: List<SeriesItem>) {
        if (items.isNotEmpty()) prefs.edit()
            .putString(key(profile, "series", categories), gson.toJson(items.take(36))).apply()
    }

    fun lastCategory(profile: String, kind: String): String? =
        prefs.getString(key(profile, "last-$kind", emptyList()), null)

    fun saveLastCategory(profile: String, kind: String, categoryId: String) {
        prefs.edit().putString(key(profile, "last-$kind", emptyList()), categoryId).apply()
    }
}
