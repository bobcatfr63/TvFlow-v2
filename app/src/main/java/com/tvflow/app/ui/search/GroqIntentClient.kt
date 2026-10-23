package com.tvflow.app.ui.search

import com.tvflow.app.BuildConfig
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Optional AI query rewriting via a server-side proxy.
 * No API secrets are shipped in the APK, and local search works without the proxy.
 */
@Singleton
class GroqIntentClient @Inject constructor() {
    suspend fun rewrite(query: String): String? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.TVFLOW_AI_ENDPOINT.trim()
        if (!endpoint.startsWith("https://") || query.length !in 18..300) return@withContext null
        try {
            val connection = URL(endpoint).openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 1200
                connection.readTimeout = 2000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val body = JSONObject().put("query", query).toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                if (connection.responseCode != 200) return@withContext null
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("normalized_query").takeIf { it.length in 2..300 && it != query }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null // Fall back to existing deterministic search.
        }
    }
}
