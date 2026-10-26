package com.tvflow.app.ui.search

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Real TMDB Cloud gateway check. Consumes one authorized TMDB request. */
object TvFlowCloudDiagnostics {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun testTmdb(context: Context): String = withContext(Dispatchers.IO) {
        val session = AiGatewayConfig(context).token()
        if (session.isBlank()) return@withContext "TMDB Cloud : aucun jeton actif."
        val payload = JSONObject().put("type", "movie").put("query", "Inception").toString()
        val request = Request.Builder()
            .url("https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-metadata")
            .header("Authorization", "Bearer $session")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val content = response.body?.string().orEmpty()
                        val json = JSONObject(content)
                        if (json.optJSONArray("results") != null) "TMDB Cloud actif : réponse reçue."
                        else "TMDB Cloud : réponse inattendue du serveur."
                    }
                    401, 403 -> "TMDB Cloud : autorisation refusée."
                    429 -> "TMDB Cloud : quota atteint ou accès expiré."
                    else -> "TMDB Cloud : erreur HTTP ${response.code}."
                }
            }
        } catch (_: Exception) {
            "TMDB Cloud : connexion indisponible."
        }
    }
}
