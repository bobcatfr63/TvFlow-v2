package com.tvflow.app.data.metadata

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.ui.search.AiGatewayConfig
import com.tvflow.app.domain.MediaType
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TmdbSourceStats(val cloud: Int = 0, val local: Int = 0, val cloudCacheHits: Int = 0, val lastSource: String = "Aucune recherche")

data class TmdbRequestDiagnostics(
    val matched: Int = 0,
    val noMatch: Int = 0,
    val unauthorized: Int = 0,
    val rateLimited: Int = 0,
    val serverErrors: Int = 0,
    val invalidResponses: Int = 0,
    val networkErrors: Int = 0
)

data class ExternalMediaMetadata(
    val synopsis: String?,
    val releaseYear: Int?,
    val releaseMonth: Int?,
    val releaseDate: String? = null,
    val imageUrl: String?,
    val rating: String?,
    val backdropUrl: String? = null,
    val genreIds: String? = null
)

@Singleton
class TmdbMetadataClient @Inject constructor(
    private val client: OkHttpClient,
    private val settingsStore: SettingsStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val _diagnostics = MutableStateFlow(TmdbRequestDiagnostics())
    val diagnostics: StateFlow<TmdbRequestDiagnostics> = _diagnostics
    private val _sources = MutableStateFlow(TmdbSourceStats())
    val sources: StateFlow<TmdbSourceStats> = _sources
    @Synchronized private fun recordSource(cloud: Boolean, cacheHit: Boolean = false) {
        val old = _sources.value
        _sources.value = if (cloud) old.copy(cloud = old.cloud + 1,
            cloudCacheHits = old.cloudCacheHits + if (cacheHit) 1 else 0,
            lastSource = if (cacheHit) "Cloud — cache serveur" else "Cloud — TMDB")
        else old.copy(local = old.local + 1, lastSource = "Clé locale TMDB")
    }

    @Synchronized private fun recordDiagnostic(kind: String) {
        val current = _diagnostics.value
        _diagnostics.value = when (kind) {
            "matched" -> current.copy(matched = current.matched + 1)
            "noMatch" -> current.copy(noMatch = current.noMatch + 1)
            "unauthorized" -> current.copy(unauthorized = current.unauthorized + 1)
            "rateLimited" -> current.copy(rateLimited = current.rateLimited + 1)
            "serverErrors" -> current.copy(serverErrors = current.serverErrors + 1)
            "invalid" -> current.copy(invalidResponses = current.invalidResponses + 1)
            else -> current.copy(networkErrors = current.networkErrors + 1)
        }
    }

    // Prevent repeated enrichment requests when TMDB rejects the configured token.
    // A different token is allowed immediately, without app restart.
    @Volatile private var rejectedCredential: String? = null
    // Throttle repeated misses within a session; cap memory for large catalogs.
    private val missedLookups = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 512
    }
    private val missCooldownMs = 6L * 60L * 60L * 1000L

    suspend fun validateCredential(rawCredential: String): Int? = withContext(Dispatchers.IO) {
        val credential = normalizeCredential(rawCredential)
        if (credential.isBlank()) return@withContext null
        val apiKeyMode = credential.matches(Regex("^[a-fA-F0-9]{32}$"))
        val urlBuilder = "https://api.themoviedb.org/3/authentication".toHttpUrl().newBuilder()
        if (apiKeyMode) urlBuilder.addQueryParameter("api_key", credential)
        val requestBuilder = Request.Builder().url(urlBuilder.build()).header("Accept", "application/json")
        if (!apiKeyMode) requestBuilder.header("Authorization", "Bearer " + credential)
        runCatching {
            client.newCall(requestBuilder.build()).execute().use { it.code }
        }.getOrNull()
    }

    /** Verified person filmography, available only when the user configured TMDB credentials. */
    suspend fun movieCreditsForPerson(person: String): List<Pair<String, Int?>>? = withContext(Dispatchers.IO) {
        val credential = settingsStore.currentTmdbToken()?.let(::normalizeCredential)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        if (credential == rejectedCredential) return@withContext null
        val apiKeyMode = credential.matches(Regex("^[a-fA-F0-9]{32}$"))
        fun request(url: okhttp3.HttpUrl): JsonObject? {
            val finalUrl = if (apiKeyMode) url.newBuilder().addQueryParameter("api_key", credential).build() else url
            val builder = Request.Builder().url(finalUrl).header("Accept", "application/json")
            if (!apiKeyMode) builder.header("Authorization", "Bearer $credential")
            return client.newCall(builder.build()).execute().use { response ->
                if (response.code == 401 || response.code == 403) rejectedCredential = credential
                if (!response.isSuccessful) return@use null
                runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }.getOrNull()
            }
        }
        val peopleUrl = "https://api.themoviedb.org/3/search/person".toHttpUrl().newBuilder()
            .addQueryParameter("query", person).addQueryParameter("language", "fr-FR").build()
        val people = request(peopleUrl)?.getAsJsonArray("results") ?: return@withContext emptyList()
        val candidate = people.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
            .firstOrNull { normalize(string(it, "name").orEmpty()) == normalize(person) }
            ?: return@withContext emptyList()
        val id = candidate.get("id")?.asInt ?: return@withContext emptyList()
        val creditsUrl = "https://api.themoviedb.org/3/person/$id/movie_credits".toHttpUrl().newBuilder()
            .addQueryParameter("language", "fr-FR").build()
        val casts = request(creditsUrl)?.getAsJsonArray("cast") ?: return@withContext emptyList()
        casts.mapNotNull { row ->
            val movie = runCatching { row.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val title = string(movie, "title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            title to parseDate(string(movie, "release_date")).first
        }.distinctBy { normalize(it.first) to it.second }.take(150)
    }

    /** Resolve an official trailer only for a focused title; no catalog-wide video requests. */
    suspend fun trailerYoutubeKey(type: MediaType, rawTitle: String, knownYear: Int?): String? =
        withContext(Dispatchers.IO) {
            val credential = settingsStore.currentTmdbToken()?.let(::normalizeCredential)?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            if (credential == rejectedCredential) return@withContext null
            val apiKeyMode = credential.matches(Regex("^[a-fA-F0-9]{32}$"))
            fun request(url: okhttp3.HttpUrl): JsonObject? {
                val address = if (apiKeyMode) url.newBuilder().addQueryParameter("api_key", credential).build() else url
                val builder = Request.Builder().url(address).header("Accept", "application/json")
                if (!apiKeyMode) builder.header("Authorization", "Bearer $credential")
                return runCatching {
                    client.newCall(builder.build()).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                    }
                }.getOrNull()
            }
            val query = TmdbTitleCleaner.clean(rawTitle)
            if (query.length < 2) return@withContext null
            val path = if (type == MediaType.VOD) "movie" else "tv"
            val searchUrl = "https://api.themoviedb.org/3/search/$path".toHttpUrl().newBuilder()
                .addQueryParameter("query", query).addQueryParameter("language", "fr-FR").build()
            val match = request(searchUrl)?.getAsJsonArray("results")
                ?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                ?.mapNotNull { candidate -> scoreCandidate(type, query, knownYear, candidate)?.let { candidate to it } }
                ?.maxByOrNull { it.second }?.takeIf { it.second >= 80 }?.first ?: return@withContext null
            val id = runCatching { match.get("id").asInt }.getOrNull() ?: return@withContext null
            fun videos(language: String): List<JsonObject> {
                val address = "https://api.themoviedb.org/3/$path/$id/videos".toHttpUrl().newBuilder()
                    .addQueryParameter("language", language).build()
                return request(address)?.getAsJsonArray("results")
                    ?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }.orEmpty()
            }
            val results = videos("fr-FR").ifEmpty { videos("en-US") }
            results.filter { string(it, "site").equals("YouTube", true) && string(it, "type") in listOf("Trailer", "Teaser") }
                .sortedWith(compareByDescending<JsonObject> { string(it, "official") == "true" }
                    .thenBy { string(it, "type") != "Trailer" })
                .firstNotNullOfOrNull { string(it, "key")?.takeIf { key -> key.matches(Regex("[A-Za-z0-9_-]{11}")) } }
        }

    // Single-flight per title and short-lived bounded RAM cache. No catalog-wide prefetch.
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<ExternalMediaMetadata?>>()
    private val foundCache = object : LinkedHashMap<String, Pair<Long, ExternalMediaMetadata>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, ExternalMediaMetadata>>?) = size > 256
    }
    suspend fun search(type: MediaType, rawTitle: String, knownYear: Int?): ExternalMediaMetadata? {
        val identity = type.name + "|" + rawTitle.trim().lowercase(java.util.Locale.ROOT) + "|" + knownYear +
            "|" + AiGatewayConfig(context).token().hashCode() + "|" + settingsStore.currentTmdbToken().hashCode()
        synchronized(foundCache) {
            foundCache[identity]?.let { if (System.currentTimeMillis() - it.first < 30 * 60_000L) return it.second }
        }
        val mine = kotlinx.coroutines.CompletableDeferred<ExternalMediaMetadata?>()
        val running = inFlight.putIfAbsent(identity, mine)
        if (running != null) return running.await()
        try {
            val result = searchUncached(type, rawTitle, knownYear)
            if (result != null) synchronized(foundCache) { foundCache[identity] = System.currentTimeMillis() to result }
            mine.complete(result)
            return result
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(identity, mine)
        }
    }

    private suspend fun searchUncached(
        type: MediaType,
        rawTitle: String,
        knownYear: Int?
    ): ExternalMediaMetadata? = withContext(Dispatchers.IO) {
        val credential = settingsStore.currentTmdbToken()?.let(::normalizeCredential)?.takeIf { it.isNotBlank() }
        val cloudToken = AiGatewayConfig(context).token().takeIf { it.isNotBlank() }
        if (credential == null && cloudToken == null) {
            Log.d("TVFlowTmdb", "metadata unavailable: no configured TMDB or cloud session")
            return@withContext null
        }
        if (credential != null && credential == rejectedCredential) return@withContext null
        val inferredYear = knownYear ?: extractYear(rawTitle)
        val query = TmdbTitleCleaner.clean(rawTitle)
        if (query.length < 2) return@withContext null
        val lookupKey = "${type.name}:${normalize(query)}:${inferredYear ?: 0}:${credential.hashCode()}"
        synchronized(missedLookups) {
            val lastMiss = missedLookups[lookupKey]
            if (lastMiss != null && System.currentTimeMillis() - lastMiss < missCooldownMs) {
                return@withContext null
            }
        }

        val endpoint = when (type) {
            MediaType.VOD -> "https://api.themoviedb.org/3/search/movie"
            MediaType.SERIES -> "https://api.themoviedb.org/3/search/tv"
        }
        val apiKeyMode = credential?.matches(Regex("^[a-fA-F0-9]{32}$")) == true
        val urlBuilder = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("language", "fr-FR")
            .addQueryParameter("include_adult", "false")
        if (apiKeyMode) {
            urlBuilder.addQueryParameter("api_key", credential)
        }
        val url = urlBuilder.build()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
        if (!apiKeyMode && credential != null) {
            requestBuilder.header("Authorization", "Bearer " + credential)
        }
        val localRequest = requestBuilder.build()
        val cloudRequest = cloudToken?.let { session ->
            val payload = JSONObject().put("type", if (type == MediaType.VOD) "movie" else "tv")
                .put("query", query).toString()
            Request.Builder().url("https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-metadata")
                .header("Authorization", "Bearer $session")
                .post(payload.toRequestBody("application/json".toMediaType())).build()
        }
        var cloudUsed = false
        val response = try {
            val remote = cloudRequest?.let { client.newCall(it).execute() }
            if (remote != null && remote.isSuccessful) {
                cloudUsed = true
                remote
            } else {
                remote?.close()
                if (credential == null) return@withContext null
                client.newCall(localRequest).execute()
            }
        } catch (error: java.io.IOException) {
            if (credential == null) return@withContext null
            try { client.newCall(localRequest).execute() } catch (_: java.io.IOException) { return@withContext null }
        }
        response.use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) rejectedCredential = credential
                recordDiagnostic(when (response.code) {
                    401, 403 -> "unauthorized"
                    429 -> "rateLimited"
                    else -> "serverErrors"
                })
                Log.w("TVFlowTmdb", "type=$type query=$query year=$inferredYear http=${response.code}")
                return@withContext null
            }
            val body = response.body?.string().orEmpty()
            val results = runCatching {
                JsonParser.parseString(body).asJsonObject.getAsJsonArray("results")
            }.getOrNull() ?: run {
                recordDiagnostic("invalid")
                Log.w("TVFlowTmdb", "type=$type query=$query year=$inferredYear invalid_json")
                return@withContext null
            }

            val scored = results
                .mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
                .mapNotNull { candidate ->
                    scoreCandidate(type, query, inferredYear, candidate)?.let { score -> candidate to score }
                }
            val bestPair = scored.maxByOrNull { it.second }
            Log.d(
                "TVFlowTmdb",
                "type=$type query=$query year=$inferredYear results=${results.size()} scored=${scored.size} best=${bestPair?.second}"
            )
            val best = bestPair?.takeIf { it.second >= 80 }?.first ?: run {
                recordDiagnostic("noMatch")
                synchronized(missedLookups) { missedLookups[lookupKey] = System.currentTimeMillis() }
                return@withContext null
            }
            recordSource(cloudUsed, cloudUsed && runCatching {
                JsonParser.parseString(body).asJsonObject.get("cache")?.asString == "hit"
            }.getOrDefault(false))
            recordDiagnostic("matched")
            synchronized(missedLookups) { missedLookups.remove(lookupKey) }

            val date = string(best, if (type == MediaType.VOD) "release_date" else "first_air_date")
            val parsed = parseDate(date)
            val posterPath = string(best, "poster_path")
            val backdropPath = string(best, "backdrop_path")
            Log.d("TVFlowTmdb", "type=$type match=true backdrop=" + if (backdropPath.isNullOrBlank()) "missing" else "present")
            ExternalMediaMetadata(
                genreIds = best.getAsJsonArray("genre_ids")?.mapNotNull { id -> runCatching { id.asInt }.getOrNull() }?.distinct()?.joinToString(",")?.takeIf { it.isNotBlank() },
                synopsis = string(best, "overview")?.takeIf { it.isNotBlank() },
                releaseYear = parsed.first,
                releaseMonth = parsed.second,
                releaseDate = date?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) },
                imageUrl = posterPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500" + it },
                backdropUrl = backdropPath?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w1280" + it },
                rating = best.get("vote_average")?.takeIf { !it.isJsonNull }?.asDouble
                    ?.takeIf { it > 0.0 }
                    ?.let { String.format(java.util.Locale.US, "%.1f", it) }
            )
        }
    }

    private fun scoreCandidate(
        type: MediaType,
        query: String,
        knownYear: Int?,
        candidate: JsonObject
    ): Int? {
        val candidateTitle = string(candidate, if (type == MediaType.VOD) "title" else "name") ?: string(candidate, if (type == MediaType.VOD) "original_title" else "original_name") ?: return null
        val normalizedQuery = normalize(query)
        val normalizedCandidate = normalize(candidateTitle)
        val originalTitle = string(candidate, if (type == MediaType.VOD) "original_title" else "original_name")?.let(::normalize)
        var score = when {
            normalizedQuery == normalizedCandidate || normalizedQuery == originalTitle -> 100
            normalizedQuery.length >= 6 && normalizedCandidate.length >= 6 &&
                (" $normalizedCandidate ".contains(" $normalizedQuery ") ||
                    " $normalizedQuery ".contains(" $normalizedCandidate ")) -> 82
            else -> return null
        }

        val date = string(candidate, if (type == MediaType.VOD) "release_date" else "first_air_date")
        val candidateYear = parseDate(date).first
        if (knownYear != null && candidateYear != null) {
            val delta = abs(knownYear - candidateYear)
            if (delta > 1) return null
            score += if (delta == 0) 20 else 5
        }
        return score
    }

    private fun cleanTitle(value: String): String =
        value
            .replace(Regex("""^\s*(?:FR|EN|VF|VOSTFR|MULTI)\s*[-:|]+\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[[^]]*]|\([^)]*\)"""), " ")
            .replace(Regex("""\b(19|20)\d{2}\b"""), " ")
            .replace(Regex("""\b(FHD|UHD|4K|HD|VF|VOSTFR|MULTI|CAM|TELESYNC|TS|WEBRIP|WEB-DL|BLURAY|BDRIP)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""[._|]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun extractYear(value: String): Int? =
        Regex("""\b(?:19|20)\d{2}\b""")
            .findAll(value)
            .mapNotNull { it.value.toIntOrNull() }
            .lastOrNull()

    private fun normalizeCredential(value: String): String =
        value.trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private fun parseDate(value: String?): Pair<Int?, Int?> {
        if (value.isNullOrBlank()) return null to null
        val match = Regex("""((?:19|20)\d{2})[-/.](\d{1,2})""").find(value)
        if (match != null) {
            val year = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 }
            return year to month
        }
        val year = Regex("""\b(?:19|20)\d{2}\b""").find(value)?.value?.toIntOrNull()
        return year to null
    }

    private fun string(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf { !it.isJsonNull }?.asString
}
