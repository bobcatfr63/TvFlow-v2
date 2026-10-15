package com.tvflow.app.data.backup

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
    suspend fun exportEncrypted(password: CharArray): String {
        val profiles = credentialsStore.profiles.first()
        val data = JSONObject()
        data.put("schema", 1)
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
            rows.put(p)
        }
        data.put("profiles", rows)
        return PortableBackupCrypto.encrypt(data.toString().toByteArray(Charsets.UTF_8), password)
    }

    suspend fun importEncrypted(encrypted: String, password: CharArray): Int {
        val text = PortableBackupCrypto.decrypt(encrypted, password).toString(Charsets.UTF_8)
        val data = JSONObject(text)
        require(data.getInt("schema") == 1) { "Version de sauvegarde incompatible" }
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
                live, vod, series
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
            val present = dao.getAllChannels(effective.id).map { it.streamId }.toSet()
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
        return profiles.size
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
        val series: Set<Int>
    )
}
