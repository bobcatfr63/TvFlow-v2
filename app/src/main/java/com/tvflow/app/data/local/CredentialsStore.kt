package com.tvflow.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tvflow.app.domain.ServerProfile
import com.tvflow.app.domain.XtreamCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tvflow_settings")

@Singleton
class CredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val KEY_ALIAS = "tvflow_xtream_credentials"
        const val ENCRYPTED_PREFIX = "enc:v1:"
    }

    private object Keys {
        val server = stringPreferencesKey("server")
        val username = stringPreferencesKey("username")
        val password = stringPreferencesKey("password")
        val profiles = stringPreferencesKey("server_profiles_v2")
        val activeProfileId = stringPreferencesKey("active_profile_id")
    }

    val profiles: Flow<List<ServerProfile>> = context.dataStore.data.map { prefs ->
        decodeProfiles(decryptIfNeeded(prefs[Keys.profiles])).ifEmpty {
            val server = prefs[Keys.server]
            val username = prefs[Keys.username]
            val password = prefs[Keys.password]
            if (server.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) emptyList()
            else listOf(ServerProfile("legacy", "Serveur principal", XtreamCredentials(server, username, password)))
        }
    }

    val activeProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.activeProfileId] ?: run {
            val legacyPresent = !prefs[Keys.server].isNullOrBlank() && !prefs[Keys.username].isNullOrBlank()
            if (legacyPresent) "legacy" else decodeProfiles(decryptIfNeeded(prefs[Keys.profiles])).firstOrNull()?.id
        }
    }

    val activeProfile: Flow<ServerProfile?> = context.dataStore.data.map { prefs ->
        val list = decodeProfiles(decryptIfNeeded(prefs[Keys.profiles])).ifEmpty {
            val server = prefs[Keys.server]
            val username = prefs[Keys.username]
            val password = prefs[Keys.password]
            if (server.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) emptyList()
            else listOf(ServerProfile("legacy", "Serveur principal", XtreamCredentials(server, username, password)))
        }
        val activeId = prefs[Keys.activeProfileId] ?: list.firstOrNull()?.id
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    val credentials: Flow<XtreamCredentials?> = activeProfile.map { it?.credentials }

    suspend fun save(value: XtreamCredentials, displayName: String? = null) {
        val normalizedName = displayName?.trim().takeUnless { it.isNullOrBlank() }
            ?: value.serverUrl.substringAfter("://").substringBefore('/').ifBlank { "Serveur IPTV" }
        val existingProfiles = profiles.first().toMutableList()
        val existingActive = activeProfileId.first()
        val matchingIndex = existingProfiles.indexOfFirst {
            it.credentials.serverUrl.equals(value.serverUrl, true) && it.credentials.username == value.username
        }
        val profile = if (matchingIndex >= 0) {
            existingProfiles[matchingIndex].copy(name = normalizedName, credentials = value)
        } else {
            ServerProfile(stableId(value), normalizedName, value)
        }
        if (matchingIndex >= 0) existingProfiles[matchingIndex] = profile else existingProfiles += profile
        persist(existingProfiles, profile.id)

        // Remove legacy single-account keys once the profile model has been materialized.
        if (existingActive == "legacy" && profile.id != "legacy") {
            context.dataStore.edit {
                it.remove(Keys.server); it.remove(Keys.username); it.remove(Keys.password)
            }
        }
    }

    suspend fun switchProfile(profileId: String) {
        val list = profiles.first()
        require(list.any { it.id == profileId }) { "Profil IPTV introuvable" }
        context.dataStore.edit { it[Keys.activeProfileId] = profileId }
    }

    suspend fun deleteProfile(profileId: String) {
        val list = profiles.first().filterNot { it.id == profileId }
        val active = activeProfileId.first()
        persist(list, if (active == profileId) list.firstOrNull()?.id else active)
        if (profileId == "legacy") {
            context.dataStore.edit {
                it.remove(Keys.server); it.remove(Keys.username); it.remove(Keys.password)
            }
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(Keys.profiles)
            it.remove(Keys.activeProfileId)
            it.remove(Keys.server)
            it.remove(Keys.username)
            it.remove(Keys.password)
        }
    }

    private suspend fun persist(list: List<ServerProfile>, activeId: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.profiles] = encrypt(encodeProfiles(list))
            if (activeId == null) prefs.remove(Keys.activeProfileId) else prefs[Keys.activeProfileId] = activeId
        }
    }

    private fun encodeProfiles(list: List<ServerProfile>): String = JSONArray().apply {
        list.forEach { profile ->
            put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("server", profile.credentials.serverUrl)
                put("username", profile.credentials.username)
                put("password", profile.credentials.password)
                put("outputFormat", profile.credentials.outputFormat)
            })
        }
    }.toString()

    private fun decodeProfiles(raw: String?): List<ServerProfile> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    ServerProfile(
                        id = obj.getString("id"),
                        name = obj.optString("name", "Serveur IPTV"),
                        credentials = XtreamCredentials(
                            serverUrl = obj.getString("server"),
                            username = obj.getString("username"),
                            password = obj.getString("password"),
                            outputFormat = obj.optString("outputFormat", "ts")
                        )
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun stableId(credentials: XtreamCredentials): String {
        val raw = "${credentials.serverUrl.trim().lowercase()}|${credentials.username}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return "p_" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(plainText: String): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, credentialsKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrElse { throw IllegalStateException("Impossible de chiffrer les identifiants IPTV", it) }

    private fun decryptIfNeeded(value: String?): String? {
        if (value == null || !value.startsWith(ENCRYPTED_PREFIX)) return value
        return runCatching {
            val payload = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            require(payload.size > 12) { "Données chiffrées invalides" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, credentialsKey(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun credentialsKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }
}
