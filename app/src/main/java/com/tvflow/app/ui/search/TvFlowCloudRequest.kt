package com.tvflow.app.ui.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Public request only: never carries IPTV credentials or privileged Supabase keys. */
object TvFlowCloudRequest {
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    suspend fun send(email: String, device: String): String = withContext(Dispatchers.IO) {
        val normalized = email.trim()
        if (normalized.length !in 5..254 || !normalized.contains('@')) return@withContext "Adresse e-mail invalide"
        val body = JSONObject().put("email", normalized).put("device_name", device.take(60)).toString()
        val req = Request.Builder()
            .url("https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-access-request")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        runCatching {
            http.newCall(req).execute().use { response ->
                when (response.code) {
                    202 -> "Demande envoyée ! Elle sera examinée dans TVFlow Manager."
                    429 -> "Limite atteinte : réessaie demain."
                    400 -> "Vérifie ton adresse e-mail."
                    else -> "Envoi indisponible (HTTP ${response.code}). Réessaie plus tard."
                }
            }
        }.getOrElse { "Connexion indisponible : réessaie plus tard." }
    }
}
