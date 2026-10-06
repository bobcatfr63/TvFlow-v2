package com.tvflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
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
    val tvLauncherEnabled: Boolean = false
)

data class LastLiveChannel(
    val streamId: Int,
    val name: String,
    val categoryId: String
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
            tvLauncherEnabled = prefs[Keys.tvLauncherEnabled] ?: false
        )
    }

    val parentalPinConfigured: Flow<Boolean> = context.playerSettingsDataStore.data.map { !it[Keys.pinHash].isNullOrBlank() }
    val tmdbToken: Flow<String?> = context.playerSettingsDataStore.data.map { it[Keys.tmdbToken]?.takeIf(String::isNotBlank) }
    val lastReleaseNotesVersion: Flow<String?> = context.playerSettingsDataStore.data.map {
        it[Keys.lastReleaseNotesVersion]?.takeIf(String::isNotBlank)
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
    suspend fun setTmdbToken(value: String) = context.playerSettingsDataStore.edit { prefs ->
        val token = value.trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (token.isBlank()) prefs.remove(Keys.tmdbToken) else prefs[Keys.tmdbToken] = token
    }
    suspend fun currentTmdbToken(): String? = tmdbToken.first()

    suspend fun markReleaseNotesShown(versionName: String) = context.playerSettingsDataStore.edit {
        it[Keys.lastReleaseNotesVersion] = versionName
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
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
