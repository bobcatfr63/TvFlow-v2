package com.tvflow.app.ui.search

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Invite-only enrollment. Never transports Xtream credentials or the IPTV catalog. */
class TvFlowCloudEnrollment(private val context: Context) {
    companion object {
        const val INTENT_ENDPOINT = "https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-ai/intent"
        private const val REGISTER_ENDPOINT = "https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-ai/register"
    }
    private val http = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()

    suspend fun activate(code: String): Boolean = withContext(Dispatchers.IO) {
        val invitation = code.trim()
        if (invitation.length !in 24..128) return@withContext false
        val prefs = context.getSharedPreferences("tvflow_cloud_installation", Context.MODE_PRIVATE)
        val installation = prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            if (!prefs.edit().putString("installation_id", it).commit()) return@withContext false
        }
        val payload = JSONObject().put("invite_code", invitation).put("installation_id", installation).toString()
        val request = Request.Builder().url(REGISTER_ENDPOINT)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.string()?.takeIf { it.length < 1024 } ?: return@use false
                val accessToken = JSONObject(body).optString("access_token")
                if (accessToken.length !in 32..256) return@use false
                AiGatewayConfig(context).save(INTENT_ENDPOINT, accessToken)
                true
            }
        }.getOrDefault(false)
    }
}
