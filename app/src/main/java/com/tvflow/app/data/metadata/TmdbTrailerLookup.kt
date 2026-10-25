package com.tvflow.app.data.metadata

import com.google.gson.JsonParser
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.domain.MediaType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class TmdbTrailerLookup @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsStore
) {
    suspend fun youtubeKey(type: MediaType, rawTitle: String, year: Int?): String? = withContext(Dispatchers.IO) {
        val token = settings.currentTmdbToken()?.trim()?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val title = TmdbTitleCleaner.clean(rawTitle).takeIf { it.length >= 2 } ?: return@withContext null
        val apiKey = token.matches(Regex("[a-fA-F0-9]{32}"))
        fun get(path: String, args: Map<String, String> = emptyMap()): com.google.gson.JsonObject? {
            val url = ("https://api.themoviedb.org/3/" + path).toHttpUrl().newBuilder().apply {
                args.forEach { (key, value) -> addQueryParameter(key, value) }
                if (apiKey) addQueryParameter("api_key", token)
            }.build()
            val request = Request.Builder().url(url).apply {
                if (!apiKey) header("Authorization", "Bearer $token")
            }.build()
            return runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                }
            }.getOrNull()
        }
        val path = if (type == MediaType.VOD) "movie" else "tv"
        val search = get("search/$path", mapOf("query" to title, "language" to "fr-FR"))
            ?: return@withContext null
        val results = search.getAsJsonArray("results") ?: return@withContext null
        val match = results.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
            .firstOrNull { candidate ->
                val date = candidate.get(if (type == MediaType.VOD) "release_date" else "first_air_date")
                    ?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                year == null || date.startsWith(year.toString())
            } ?: return@withContext null
        val id = match.get("id")?.asInt ?: return@withContext null
        fun selectTrailer(language: String): String? {
            val results = get("$path/$id/videos", mapOf("language" to language))?.getAsJsonArray("results")
                ?: return null
            return results.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                .firstOrNull { video ->
                    video.get("site")?.asString == "YouTube" &&
                        video.get("type")?.asString in listOf("Trailer", "Teaser")
                }?.get("key")?.asString?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }
        selectTrailer("fr-FR") ?: selectTrailer("en-US")
    }
}
