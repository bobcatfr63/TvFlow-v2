package com.tvflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import org.json.JSONObject
import javax.inject.Singleton

private val Context.playerSettingsDataStore by preferencesDataStore(name = "tvflow_player_settings")

data class PlayerPreferences(
    val bufferPreset: String = "FAST",
    val videoScale: String = "FIT",
    val subtitlesEnabled: Boolean = true,
    val accent: String = "BLUE",
    val refreshIntervalDays: Int = 0,
    val hideOfflineStreams: Boolean = false,
    val autoStartOnTvBoot: Boolean = false,
    val resumeLastLiveOnTvBoot: Boolean = false,
    val tvLauncherEnabled: Boolean = false,
    val autoNextEpisode: Boolean = true,
    val nextEpisodeCountdownSeconds: Int = 10,
    val mobileHomePreviewMode: String = "WIFI"
)

data class LastLiveChannel(
    val streamId: Int,
    val name: String,
    val categoryId: String
)

data class TastePreferences(
    val onboardingCompleted: Boolean = false,
    val themes: Set<String> = emptySet()
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val buffer = stringPreferencesKey("buffer_preset")
        val scale = stringPreferencesKey("video_scale")
        val subtitles = booleanPreferencesKey("subtitles_enabled")
        val accent = stringPreferencesKey("accent")
        val pinHash = stringPreferencesKey("parental_pin_hash")
        val refreshIntervalDays = intPreferencesKey("refresh_interval_days")
        val hideOfflineStreams = booleanPreferencesKey("hide_offline_streams")
        val autoStartOnTvBoot = booleanPreferencesKey("auto_start_on_tv_boot")
        val resumeLastLiveOnTvBoot = booleanPreferencesKey("resume_last_live_on_tv_boot")
        val tvLauncherEnabled = booleanPreferencesKey("tv_launcher_enabled")
        val lastLiveStreamId = intPreferencesKey("last_live_stream_id")
        val lastLiveName = stringPreferencesKey("last_live_name")
        val lastLiveCategoryId = stringPreferencesKey("last_live_category_id")
        val tmdbToken = stringPreferencesKey("tmdb_token")
        val lastReleaseNotesVersion = stringPreferencesKey("last_release_notes_version")
        val autoNextEpisode = booleanPreferencesKey("auto_next_episode")
        val nextEpisodeCountdownSeconds = intPreferencesKey("next_episode_countdown_seconds")
        val categorySectionOverrides = stringPreferencesKey("category_section_overrides")
        val hiddenMediaCategories = stringPreferencesKey("hidden_media_categories")
        val tasteOnboardingCompleted = booleanPreferencesKey("taste_onboarding_completed")
        val tasteThemes = stringPreferencesKey("taste_themes")
        val mobileHomePreviewMode = stringPreferencesKey("mobile_home_preview_mode")
    }

    val playerPreferences: Flow<PlayerPreferences> = context.playerSettingsDataStore.data.map { prefs ->
        PlayerPreferences(
            bufferPreset = prefs[Keys.buffer] ?: "FAST",
            videoScale = prefs[Keys.scale] ?: "FIT",
            subtitlesEnabled = prefs[Keys.subtitles] ?: true,
            accent = prefs[Keys.accent] ?: "BLUE",
            refreshIntervalDays = prefs[Keys.refreshIntervalDays] ?: 0,
            hideOfflineStreams = prefs[Keys.hideOfflineStreams] ?: false,
            autoStartOnTvBoot = prefs[Keys.autoStartOnTvBoot] ?: false,
            resumeLastLiveOnTvBoot = prefs[Keys.resumeLastLiveOnTvBoot] ?: false,
            tvLauncherEnabled = prefs[Keys.tvLauncherEnabled] ?: false,
            autoNextEpisode = prefs[Keys.autoNextEpisode] ?: true,
            nextEpisodeCountdownSeconds = (prefs[Keys.nextEpisodeCountdownSeconds] ?: 10).coerceIn(3, 30),
            mobileHomePreviewMode = prefs[Keys.mobileHomePreviewMode] ?: "WIFI"
        )
    }

    val parentalPinConfigured: Flow<Boolean> = context.playerSettingsDataStore.data.map { !it[Keys.pinHash].isNullOrBlank() }
    val tmdbToken: Flow<String?> = context.playerSettingsDataStore.data.map { it[Keys.tmdbToken]?.takeIf(String::isNotBlank) }
    val lastReleaseNotesVersion: Flow<String?> = context.playerSettingsDataStore.data.map {
        it[Keys.lastReleaseNotesVersion]?.takeIf(String::isNotBlank)
    }

    val tastePreferences: Flow<TastePreferences> = context.playerSettingsDataStore.data.map { prefs ->
        TastePreferences(
            onboardingCompleted = prefs[Keys.tasteOnboardingCompleted] ?: false,
            themes = prefs[Keys.tasteThemes]
                ?.split('|')
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty()
        )
    }

    val lastLiveChannel: Flow<LastLiveChannel?> = context.playerSettingsDataStore.data.map { prefs ->
        val streamId = prefs[Keys.lastLiveStreamId] ?: return@map null
        val name = prefs[Keys.lastLiveName].orEmpty()
        val categoryId = prefs[Keys.lastLiveCategoryId].orEmpty()
        if (streamId > 0 && name.isNotBlank()) LastLiveChannel(streamId, name, categoryId) else null
    }

    suspend fun setBufferPreset(value: String) = context.playerSettingsDataStore.edit { it[Keys.buffer] = value }
    suspend fun setVideoScale(value: String) = context.playerSettingsDataStore.edit { it[Keys.scale] = value }
    suspend fun setSubtitlesEnabled(value: Boolean) = context.playerSettingsDataStore.edit { it[Keys.subtitles] = value }
    suspend fun setAccent(value: String) = context.playerSettingsDataStore.edit { it[Keys.accent] = value }
    suspend fun setRefreshIntervalDays(value: Int) = context.playerSettingsDataStore.edit { it[Keys.refreshIntervalDays] = value }
    suspend fun setHideOfflineStreams(value: Boolean) = context.playerSettingsDataStore.edit { it[Keys.hideOfflineStreams] = value }
    suspend fun setAutoStartOnTvBoot(value: Boolean) = context.playerSettingsDataStore.edit {
        it[Keys.autoStartOnTvBoot] = value
        if (!value) it[Keys.resumeLastLiveOnTvBoot] = false
    }
    suspend fun setResumeLastLiveOnTvBoot(value: Boolean) = context.playerSettingsDataStore.edit {
        it[Keys.resumeLastLiveOnTvBoot] = value
        if (value) it[Keys.autoStartOnTvBoot] = true
    }
    suspend fun setTvLauncherEnabled(value: Boolean) = context.playerSettingsDataStore.edit {
        it[Keys.tvLauncherEnabled] = value
    }
    suspend fun setMobileHomePreviewMode(value: String) = context.playerSettingsDataStore.edit {
        it[Keys.mobileHomePreviewMode] = value
    }

    suspend fun setAutoNextEpisode(value: Boolean) = context.playerSettingsDataStore.edit {
        it[Keys.autoNextEpisode] = value
    }
    suspend fun setNextEpisodeCountdownSeconds(value: Int) = context.playerSettingsDataStore.edit {
        it[Keys.nextEpisodeCountdownSeconds] = value.coerceIn(3, 30)
    }
    suspend fun setTmdbToken(value: String) = context.playerSettingsDataStore.edit { prefs ->
        val token = value.trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (token.isBlank()) prefs.remove(Keys.tmdbToken) else prefs[Keys.tmdbToken] = token
    }
    suspend fun currentTmdbToken(): String? = tmdbToken.first()

    suspend fun markReleaseNotesShown(versionName: String) = context.playerSettingsDataStore.edit {
        it[Keys.lastReleaseNotesVersion] = versionName
    }

    suspend fun saveTastePreferences(themes: Set<String>, completed: Boolean = true) =
        context.playerSettingsDataStore.edit { prefs ->
            prefs[Keys.tasteOnboardingCompleted] = completed
            prefs[Keys.tasteThemes] = themes
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .joinToString("|")
        }

    fun hiddenMediaCategories(profileId: String): Flow<Set<String>> =
        context.playerSettingsDataStore.data.map { prefs ->
            decodeCategoryOverrides(prefs[Keys.hiddenMediaCategories])[profileId].orEmpty().keys
        }

    suspend fun setMediaCategoryHidden(profileId: String, categoryKey: String, hidden: Boolean) =
        context.playerSettingsDataStore.edit { prefs ->
            val all = decodeCategoryOverrides(prefs[Keys.hiddenMediaCategories]).toMutableMap()
            val selected = all[profileId].orEmpty().toMutableMap()
            if (hidden) selected[categoryKey] = "1" else selected.remove(categoryKey)
            if (selected.isEmpty()) all.remove(profileId) else all[profileId] = selected
            if (all.isEmpty()) prefs.remove(Keys.hiddenMediaCategories)
            else prefs[Keys.hiddenMediaCategories] = encodeCategoryOverrides(all)
        }

    fun categorySectionOverrides(profileId: String): Flow<Map<String, String>> =
        context.playerSettingsDataStore.data.map { prefs ->
            decodeCategoryOverrides(prefs[Keys.categorySectionOverrides])[profileId].orEmpty()
        }

    suspend fun setCategorySectionOverride(profileId: String, categoryKey: String, section: String?) =
        context.playerSettingsDataStore.edit { prefs ->
            val all = decodeCategoryOverrides(prefs[Keys.categorySectionOverrides]).toMutableMap()
            val profile = all[profileId].orEmpty().toMutableMap()
            if (section.isNullOrBlank()) profile.remove(categoryKey) else profile[categoryKey] = section
            if (profile.isEmpty()) all.remove(profileId) else all[profileId] = profile
            if (all.isEmpty()) prefs.remove(Keys.categorySectionOverrides)
            else prefs[Keys.categorySectionOverrides] = encodeCategoryOverrides(all)
        }

    private fun mediaFavoriteKey(profileId: String, kind: String) =
        stringPreferencesKey("media_favorites_${kind}_${profileId}")

    fun observeMediaFavorites(profileId: String, kind: String): Flow<Set<Int>> =
        context.playerSettingsDataStore.data.map { prefs ->
            prefs[mediaFavoriteKey(profileId, kind)].orEmpty()
                .split(',').mapNotNull(String::toIntOrNull).toSet()
        }

    suspend fun toggleMediaFavorite(profileId: String, kind: String, mediaId: Int) {
        context.playerSettingsDataStore.edit { prefs ->
            val key = mediaFavoriteKey(profileId, kind)
            val ids = prefs[key].orEmpty().split(',').mapNotNull(String::toIntOrNull).toMutableSet()
            if (!ids.add(mediaId)) ids.remove(mediaId)
            prefs[key] = ids.sorted().joinToString(",")
        }
    }

    fun favoriteSyncTimestamp(profileId: String): Flow<Long> =
        context.playerSettingsDataStore.data.map { prefs ->
            prefs[longPreferencesKey("favorite_sync_" + profileId)] ?: 0L
        }

    suspend fun markFavoriteSyncChanged(profileId: String, timestamp: Long = System.currentTimeMillis()) =
        context.playerSettingsDataStore.edit {
            it[longPreferencesKey("favorite_sync_" + profileId)] = timestamp
        }

    suspend fun setLastLiveChannel(streamId: Int, name: String, categoryId: String) = context.playerSettingsDataStore.edit {
        it[Keys.lastLiveStreamId] = streamId
        it[Keys.lastLiveName] = name
        it[Keys.lastLiveCategoryId] = categoryId
    }

    suspend fun setParentalPin(pin: String) {
        require(pin.length in 4..8 && pin.all(Char::isDigit)) { "Le PIN doit contenir 4 à 8 chiffres" }
        context.playerSettingsDataStore.edit { it[Keys.pinHash] = hash(pin) }
    }
    suspend fun clearParentalPin() = context.playerSettingsDataStore.edit { it.remove(Keys.pinHash) }
    suspend fun verifyParentalPin(pin: String): Boolean = context.playerSettingsDataStore.data.map { it[Keys.pinHash] == hash(pin) }.first()

    private fun decodeCategoryOverrides(raw: String?): Map<String, Map<String, String>> = runCatching {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = JSONObject(raw)
        buildMap {
            root.keys().forEach { profileId ->
                val profileJson = root.optJSONObject(profileId) ?: return@forEach
                put(
                    profileId,
                    buildMap {
                        profileJson.keys().forEach { key ->
                            profileJson.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                        }
                    }
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun encodeCategoryOverrides(value: Map<String, Map<String, String>>): String {
        val root = JSONObject()
        value.forEach { (profileId, overrides) ->
            val profile = JSONObject()
            overrides.forEach { (key, section) -> profile.put(key, section) }
            root.put(profileId, profile)
        }
        return root.toString()
    }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
