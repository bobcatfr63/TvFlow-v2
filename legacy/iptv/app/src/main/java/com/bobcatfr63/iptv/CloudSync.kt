package com.bobcatfr63.iptv

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CloudSession(val userId: String, val accessToken: String, val refreshToken: String)
data class CloudDevice(val id: String, val name: String, val platform: String)
data class RemoteCommand(val id: Long, val command: String, val playable: Playable? = null)

class BobcatCloud(private val context: Context) {
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val prefs = EncryptedSharedPreferences.create(
        context, "bobcat_cloud_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun session(): CloudSession? {
        val user = prefs.getString("user", null) ?: return null
        val access = prefs.getString("access", null) ?: return null
        return CloudSession(user, access, prefs.getString("refresh", "").orEmpty())
    }

    suspend fun signIn(email: String, password: String): CloudSession = auth("token?grant_type=password", email, password)
    suspend fun signUp(email: String, password: String): CloudSession = auth("signup", email, password)

    suspend fun refreshSession(): CloudSession = withContext(Dispatchers.IO) {
        val current = requireSession()
        if (current.refreshToken.isBlank()) throw IllegalStateException("Session cloud expirée")
        val body = JSONObject().put("refresh_token", current.refreshToken).toString()
        val request = baseRequest("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
            .post(body.toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Renouvellement cloud impossible")
            val root = JSONObject(text)
            CloudSession(root.getJSONObject("user").getString("id"), root.getString("access_token"), root.getString("refresh_token")).also(::saveSession)
        }
    }

    private suspend fun auth(path: String, email: String, password: String): CloudSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val request = baseRequest("${BuildConfig.SUPABASE_URL}/auth/v1/$path", false)
            .post(body.toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(JSONObject(text).optString("msg", JSONObject(text).optString("message", "Connexion cloud refusée")))
            val root = JSONObject(text)
            val token = root.optString("access_token")
            val user = root.optJSONObject("user")?.optString("id").orEmpty()
            if (token.isBlank() || user.isBlank()) throw IllegalStateException("Confirmez votre adresse e-mail puis connectez-vous.")
            CloudSession(user, token, root.optString("refresh_token")).also(::saveSession)
        }
    }

    fun signOut() = prefs.edit().clear().apply()
    fun currentDeviceId(): String? = prefs.getString("device_id", null)
    fun preferredTvId(): String? = prefs.getString("preferred_tv", null)
    fun pairTv(id: String) = prefs.edit().putString("preferred_tv", id).apply()

    suspend fun registerDevice(): CloudDevice = withContext(Dispatchers.IO) {
        val existing = prefs.getString("device_id", null)
        if (existing != null) return@withContext CloudDevice(existing, Build.MODEL, platform())
        val session = requireSession()
        val body = JSONObject().put("user_id", session.userId).put("name", Build.MODEL).put("platform", platform()).toString()
        val request = authedRequest("${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_devices?select=id,name,platform", session)
            .header("Prefer", "return=representation").post(body.toRequestBody(jsonType)).build()
        executeJsonArray(request).getJSONObject(0).let {
            CloudDevice(it.getString("id"), it.getString("name"), it.getString("platform")).also { device -> prefs.edit().putString("device_id", device.id).apply() }
        }
    }

    suspend fun devices(): List<CloudDevice> = withContext(Dispatchers.IO) {
        val request = authedRequest("${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_devices?select=id,name,platform&order=last_seen_at.desc", requireSession()).get().build()
        val array = executeJsonArray(request)
        buildList { for (i in 0 until array.length()) array.getJSONObject(i).let { add(CloudDevice(it.getString("id"), it.getString("name"), it.getString("platform"))) } }
    }

    suspend fun pushState(profileId: String, kind: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        val session = requireSession()
        val body = JSONObject().put("user_id", session.userId).put("profile_id", profileId).put("state_kind", kind).put("payload", payload).toString()
        val request = authedRequest("${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_sync_state?on_conflict=user_id,profile_id,state_kind", session)
            .header("Prefer", "resolution=merge-duplicates").post(body.toRequestBody(jsonType)).build()
        execute(request)
    }

    suspend fun pullState(profileId: String, kind: String): JSONObject? = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_sync_state?profile_id=eq.$profileId&state_kind=eq.$kind&select=payload&limit=1"
        val array = executeJsonArray(authedRequest(url, requireSession()).get().build())
        if (array.length() == 0) null else array.getJSONObject(0).getJSONObject("payload")
    }

    suspend fun sendOpen(targetId: String, playable: Playable) = withContext(Dispatchers.IO) {
        val session = requireSession()
        val payload = JSONObject().put("title", playable.title).put("type", playable.type.name)
            .put("key", playable.contentKey).put("category", playable.categoryId)
            .put("extension", playable.url.substringAfterLast('.', "mp4").substringBefore('?'))
        val body = JSONObject().put("user_id", session.userId).put("target_device_id", targetId).put("command", "open").put("payload", payload).toString()
        execute(authedRequest("${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_remote_commands", session).post(body.toRequestBody(jsonType)).build())
    }

    suspend fun sendControl(targetId: String, command: String) = withContext(Dispatchers.IO) {
        val session = requireSession()
        val body = JSONObject().put("user_id", session.userId).put("target_device_id", targetId).put("command", command).put("payload", JSONObject()).toString()
        execute(authedRequest("${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_remote_commands", session).post(body.toRequestBody(jsonType)).build())
    }

    suspend fun pendingCommands(deviceId: String): List<RemoteCommand> = withContext(Dispatchers.IO) {
        val session = requireSession()
        val url = "${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_remote_commands?target_device_id=eq.$deviceId&status=eq.pending&select=id,command,payload&order=created_at.asc"
        val array = executeJsonArray(authedRequest(url, session).get().build())
        buildList { for (i in 0 until array.length()) { val row=array.getJSONObject(i); val command=row.getString("command"); val p=row.getJSONObject("payload"); val playable=if(command=="open") Playable(p.getString("title"),p.optString("extension","mp4"),ContentType.valueOf(p.getString("type")),p.optString("key"),p.optString("category")) else null; add(RemoteCommand(row.getLong("id"),command,playable)) } }
    }

    suspend fun consumeCommand(id: Long) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("status", "consumed").put("consumed_at", java.time.Instant.now().toString()).toString()
        execute(authedRequest("${BuildConfig.SUPABASE_URL}/rest/v1/bobcat_remote_commands?id=eq.$id", requireSession()).patch(body.toRequestBody(jsonType)).build())
    }

    private fun saveSession(s: CloudSession) = prefs.edit().putString("user",s.userId).putString("access",s.accessToken).putString("refresh",s.refreshToken).apply()
    private fun requireSession() = session() ?: throw IllegalStateException("Compte Bobcat non connecté")
    private fun platform(): String = if (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) "tv" else "mobile"
    private fun baseRequest(url: String, auth: Boolean = true): Request.Builder = Request.Builder().url(url).header("apikey", BuildConfig.SUPABASE_KEY).header("Accept", "application/json")
    private fun authedRequest(url: String, session: CloudSession) = baseRequest(url).header("Authorization", "Bearer ${session.accessToken}")
    private fun execute(request: Request) { client.newCall(request).execute().use { if (!it.isSuccessful) throw IllegalStateException("Cloud HTTP ${it.code}: ${it.body?.string().orEmpty().take(300)}") } }
    private fun executeJsonArray(request: Request): JSONArray = client.newCall(request).execute().use { if (!it.isSuccessful) throw IllegalStateException("Cloud HTTP ${it.code}"); JSONArray(it.body?.string().orEmpty()) }
}
