package com.tvflow.app.ui.search

import android.content.Context
import com.tvflow.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Optional, authenticated intent hints. Only repository items become results. */
@Singleton
class GroqIntentClient @Inject constructor(
    @ApplicationContext context: Context,
    private val usageCounter: AiUsageCounter
) {
    private val config = AiGatewayConfig(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4500, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    @Volatile private var retryAfter = 0L
    private val hintCache = object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean = size > 64
    }
    private fun cachedHint(key: String): String? = synchronized(hintCache) {
        val entry = hintCache[key] ?: return@synchronized null
        if (entry.first < android.os.SystemClock.elapsedRealtime()) {
            hintCache.remove(key)
            null
        } else entry.second
    }
    private fun rememberHint(key: String, hint: String) = synchronized(hintCache) {
        hintCache[key] = (android.os.SystemClock.elapsedRealtime() + 30 * 60 * 1000L) to hint
    }
    private val _gatewayUsage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val gatewayUsage: kotlinx.coroutines.flow.StateFlow<String?> = _gatewayUsage

    suspend fun refreshGatewayUsage() = withContext(Dispatchers.IO) {
        val endpoint = runCatching { config.endpoint.trim() }.getOrDefault("")
        if (!AiGatewayConfig.validEndpoint(endpoint, BuildConfig.DEBUG)) return@withContext
        val token = config.token()
        if (token.length < 32) return@withContext
        try {
            val url = endpoint.replace(Regex("/v1/intent/?$"), "/v1/usage")
            if (url == endpoint) return@withContext
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val source = response.body?.source() ?: return@use
                source.request(1025)
                if (source.buffer.size > 1024) return@use
                val data = JSONObject(source.readUtf8())
                if (data.optString("scope") != "private_gateway") return@use
                val used = data.optInt("used", -1)
                val limit = data.optInt("limit", -1)
                if (used >= 0 && limit in 1..100000) _gatewayUsage.value = "IA : $used / $limit"
            }
        } catch (_: Exception) { /* Local indicator remains available. */ }
    }

    suspend fun rewrite(query: String): String? {
        val endpoint = runCatching { config.endpoint.trim() }.getOrDefault("")
        val cacheKey = endpoint + "|" + query.trim().lowercase(java.util.Locale.ROOT)
        if (query.length !in 2..300 || !AiGatewayConfig.validEndpoint(endpoint, BuildConfig.DEBUG) ||
            android.os.SystemClock.elapsedRealtime() < retryAfter) return null
        val token = withContext(Dispatchers.IO) { config.token() }
        if (token.length < 32) return null
        cachedHint(cacheKey)?.let { return it }
        // Cancel while typing rather than spend quota on partial queries.
        delay(600)
        val body = JSONObject().put("query", query).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = runCatching {
            Request.Builder().url(endpoint).header("Authorization", "Bearer $token").post(body).build()
        }.getOrNull() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            fun finish(value: String?) {
                if (!continuation.isActive) return
                if (value == null) retryAfter = android.os.SystemClock.elapsedRealtime() + 30_000
                continuation.resume(value)
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { finish(null) }
                override fun onResponse(call: Call, response: Response) {
                    val rewritten = runCatching {
                        response.use {
                            if (it.code != 200) return@use null
                            val source = it.body?.source() ?: return@use null
                            source.request(4097)
                            if (source.buffer.size > 4096) return@use null
                            JSONObject(source.readUtf8()).optString("normalized_query")
                                .takeIf { text -> text.length in 2..300 && text.none { c -> c.code < 32 } }
                        }
                    }.getOrNull()
                    if (rewritten != null) {
                        rememberHint(cacheKey, rewritten)
                        usageCounter.recordSuccess()
                    }
                    finish(rewritten)
                }
            })
        }
    }
}
