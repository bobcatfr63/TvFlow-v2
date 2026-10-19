package com.tvflow.app.data.backup

import com.tvflow.app.data.local.BackupCategoryPreference
import com.tvflow.app.data.local.BackupChannelPreference
import com.tvflow.app.data.local.CredentialsStore
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.data.local.TVFlowDao
import com.tvflow.app.domain.XtreamCredentials
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Portable profile/favorites snapshot. Only restored after successful decryption and
 * full validation. Existing profiles not present in backup are never deleted.
 */
@Singleton
class PortableBackupManager @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val settingsStore: SettingsStore,
    private val dao: TVFlowDao
) {
    suspend fun exportPlain(): String {
        val profiles = credentialsStore.profiles.first()
        val data = JSONObject()
        data.put("schema", 3)
        settingsStore.currentTmdbToken()?.let { data.put("tmdbToken", it) }
        data.put("activeProfileId", credentialsStore.activeProfileId.first())
        val rows = JSONArray()
        for (profile in profiles) {
            val p = JSONObject()
            p.put("id", profile.id)
            p.put("name", profile.name)
            p.put("server", profile.credentials.serverUrl)
            p.put("username", profile.credentials.username)
            p.put("password", profile.credentials.password)
            p.put("outputFormat", profile.credentials.outputFormat)
            p.put("liveFavorites", JSONArray((dao.getFavoriteIds(profile.id).toSet() + settingsStore.pendingLiveFavorites(profile.id).first()).sorted()))
            p.put("vodFavorites", JSONArray(settingsStore.observeMediaFavorites(profile.id, "vod").first().sorted()))
            p.put("seriesFavorites", JSONArray(settingsStore.observeMediaFavorites(profile.id, "series").first().sorted()))
            p.put("hiddenMediaCategories", JSONArray(settingsStore.hiddenMediaCategories(profile.id).first().sorted()))
            p.put("mediaCategorySections", JSONObject(settingsStore.categorySectionOverrides(profile.id).first()))
            p.put("liveCategories", JSONArray().also { arr ->
                dao.getCategoryPreferences(profile.id).forEach { row ->
                    arr.put(JSONObject().put("id", row.id).put("hidden", row.isHidden)
                        .put("locked", row.isLocked).put("order", row.customOrder))
                }
            })
            p.put("liveChannels", JSONArray().also { arr ->
                dao.getChannelPreferences(profile.id).forEach { row ->
                    if (row.isHidden || row.customOrder != null) {
                        arr.put(JSONObject().put("id", row.streamId).put("hidden", row.isHidden)
                            .put("order", row.customOrder))
                    }
                }
            })
            rows.put(p)
        }
        data.put("profiles", rows)
        return data.toString()
    }

    suspend fun importBackup(content: String, legacyPassword: CharArray? = null): Int {
        val text = if (content.startsWith("TVFLOW-BACKUP-V1:")) {
            require(legacyPassword != null) { "Ancienne sauvegarde chiffrée : mot de passe requis" }
            PortableBackupCrypto.decrypt(content, legacyPassword).toString(Charsets.UTF_8)
        } else content
        val data = JSONObject(text)
        require(data.getInt("schema") in 1..3) { "Version de sauvegarde incompatible" }
        // Old files have no TMDB field: do not erase the configured key.
        val importedTmdbToken = if (data.has("tmdbToken") && !data.isNull("tmdbToken")) {
            data.getString("tmdbToken").trim().also {
                require(it.length <= 4096 && !it.contains('\n') && !it.contains('\r')) { "Clé TMDB invalide" }
            }
        } else null
        val rows = data.getJSONArray("profiles")
        require(rows.length() in 1..50) { "Nombre de profils invalide" }
        // Validate the entire document before any persistent changes.
        val profiles = (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            val server = item.getString("server")
            val username = item.getString("username")
            val passwordValue = item.getString("password")
            require(server.isNotBlank() && username.isNotBlank() && passwordValue.isNotBlank())
            require(server.length < 4096 && username.length < 1024 && passwordValue.length < 4096)
            val live = intIds(item.getJSONArray("liveFavorites"))
            val vod = intIds(item.getJSONArray("vodFavorites"))
            val series = intIds(item.getJSONArray("seriesFavorites"))
            Snapshot(
                item.getString("id"),
                item.optString("name", "Profil IPTV"),
                XtreamCredentials(server, username, passwordValue, item.optString("outputFormat", "ts")),
                live, vod, series,
                if (data.getInt("schema") >= 2) stringIds(item.optJSONArray("hiddenMediaCategories")) else null,
                if (data.getInt("schema") >= 2) sections(item.optJSONObject("mediaCategorySections")) else null,
                if (data.getInt("schema") >= 2) categories(item.optJSONArray("liveCategories")) else null,
                if (data.getInt("schema") >= 2) channels(item.optJSONArray("liveChannels")) else null
            )
        }
        // CredentialsStore.save preserves the stable per-account ID when possible.
        // Favorited live channels must already exist in Room for flags to be applied.
        for (profile in profiles) {
            credentialsStore.save(profile.credentials, profile.name)
            val effective = credentialsStore.profiles.first().firstOrNull {
                it.credentials.serverUrl == profile.credentials.serverUrl &&
                    it.credentials.username == profile.credentials.username
            } ?: error("Profil non enregistré")
            settingsStore.replaceMediaFavorites(effective.id, "vod", profile.vod)
            settingsStore.replaceMediaFavorites(effective.id, "series", profile.series)
            // Schema 1 backups do not contain organization: preserve existing settings.
            profile.hiddenMedia?.let { settingsStore.replaceHiddenMediaCategories(effective.id, it) }
            profile.sections?.let { settingsStore.replaceCategorySectionOverrides(effective.id, it) }
            if (profile.categories != null && profile.channels != null) {
                // Keep imported preferences for catalogs that arrive after restoration.
                val pending = JSONObject()
                    .put("categories", JSONArray().also { a -> profile.categories.forEach { c ->
                        a.put(JSONObject().put("id", c.id).put("hidden", c.hidden).put("locked", c.locked)
                            .put("order", c.order))
                    } })
                    .put("channels", JSONArray().also { a -> profile.channels.forEach { c ->
                        a.put(JSONObject().put("id", c.id).put("hidden", c.hidden).put("order", c.order))
                    } })
                settingsStore.setRestoredLiveOrganization(effective.id, pending.toString())
            }
            dao.applyBackupOrganization(
                effective.id,
                profile.categories.orEmpty().map { BackupCategoryPreference(it.id, it.hidden, it.locked, it.order) },
                profile.channels.orEmpty().map { BackupChannelPreference(it.id, it.hidden, it.order) }
            )
            val present = dao.getChannelIds(effective.id).toSet()
            val ready = profile.live.intersect(present)
            settingsStore.setPendingLiveFavorites(effective.id, profile.live - ready)
            if (ready.isNotEmpty()) dao.setFavoriteIds(effective.id, ready.toList())
        }
        val original = data.optString("activeProfileId")
        val active = profiles.firstOrNull { it.id == original }
        if (active != null) {
            val effectiveId = credentialsStore.profiles.first().firstOrNull {
                it.credentials.serverUrl == active.credentials.serverUrl &&
                    it.credentials.username == active.credentials.username
            }?.id
            if (effectiveId != null) credentialsStore.switchProfile(effectiveId)
        }
        importedTmdbToken?.let { settingsStore.setTmdbToken(it) }
        return profiles.size
    }

    private data class CategoryPref(val id: String, val hidden: Boolean, val locked: Boolean, val order: Int?)
    private data class ChannelPref(val id: Int, val hidden: Boolean, val order: Int?)

    private fun stringIds(array: JSONArray?): Set<String> {
        require(array == null || array.length() <= 20_000) { "Trop de catégories masquées" }
        return (0 until (array?.length() ?: 0)).map { array!!.getString(it) }.onEach {
            require(it.isNotBlank() && it.length < 512 && (it.startsWith("VOD:") || it.startsWith("SERIES:")))
        }.toSet()
    }

    private fun sections(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        require(obj.length() <= 20_000) { "Trop de classements" }
        return buildMap {
            obj.keys().forEach { key ->
                val value = obj.getString(key)
                require(key.length < 512 && value in setOf("FRANCE", "KIDS", "OTHER"))
                put(key, value)
            }
        }
    }

    private fun categories(array: JSONArray?): List<CategoryPref> {
        require(array == null || array.length() <= 20_000) { "Trop de catégories" }
        return (0 until (array?.length() ?: 0)).map {
            val v = array!!.getJSONObject(it)
            CategoryPref(v.getString("id").also { id -> require(id.isNotBlank() && id.length < 512) },
                v.getBoolean("hidden"), v.getBoolean("locked"),
                if (v.isNull("order")) null else v.getInt("order"))
        }
    }

    private fun channels(array: JSONArray?): List<ChannelPref> {
        require(array == null || array.length() <= 100_000) { "Trop de chaînes" }
        return (0 until (array?.length() ?: 0)).map {
            val v = array!!.getJSONObject(it)
            ChannelPref(v.getInt("id").also { id -> require(id > 0) },
                v.getBoolean("hidden"), if (v.isNull("order")) null else v.getInt("order"))
        }
    }

    private fun intIds(array: JSONArray): Set<Int> {
        require(array.length() <= 100_000) { "Trop de favoris" }
        return (0 until array.length()).map { index ->
            array.getInt(index).also { require(it > 0) { "Identifiant invalide" } }
        }.toSet()
    }

    private data class Snapshot(
        val id: String,
        val name: String,
        val credentials: XtreamCredentials,
        val live: Set<Int>,
        val vod: Set<Int>,
        val series: Set<Int>,
        val hiddenMedia: Set<String>?,
        val sections: Map<String, String>?,
        val categories: List<CategoryPref>?,
        val channels: List<ChannelPref>?
    )
}
