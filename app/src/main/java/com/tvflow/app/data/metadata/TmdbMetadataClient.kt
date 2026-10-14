package com.tvflow.app.data.metadata

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tvflow.app.data.local.SettingsStore
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

data class ExternalMediaMetadata(
    val synopsis: String?,
    val releaseYear: Int?,
    val releaseMonth: Int?,
    val imageUrl: String?,
    val rating: String?
)

@Singleton
class TmdbMetadataClient @Inject constructor(
    private val client: OkHttpClient,
    private val settingsStore: SettingsStore
) {
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

    suspend fun search(
        type: MediaType,
        rawTitle: String,
        knownYear: Int?
    ): ExternalMediaMetadata? = withContext(Dispatchers.IO) {
        val credential = settingsStore.currentTmdbToken()?.let(::normalizeCredential)?.takeIf { it.isNotBlank() } ?: return@withContext null
        if (credential == rejectedCredential) return@withContext null
        val inferredYear = knownYear ?: extractYear(rawTitle)
        val query = cleanTitle(rawTitle)
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
        val apiKeyMode = credential.matches(Regex("^[a-fA-F0-9]{32}$"))
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
        if (!apiKeyMode) {
            requestBuilder.header("Authorization", "Bearer " + credential)
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) rejectedCredential = credential
                Log.w("TVFlowTmdb", "type=$type query=$query year=$inferredYear http=${response.code}")
                return@withContext null
            }
            val body = response.body?.string().orEmpty()
            val results = runCatching {
                JsonParser.parseString(body).asJsonObject.getAsJsonArray("results")
            }.getOrNull() ?: run {
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
                synchronized(missedLookups) { missedLookups[lookupKey] = System.currentTimeMillis() }
                return@withContext null
            }
            synchronized(missedLookups) { missedLookups.remove(lookupKey) }

            val date = string(best, if (type == MediaType.VOD) "release_date" else "first_air_date")
            val parsed = parseDate(date)
            val posterPath = string(best, "poster_path")
            ExternalMediaMetadata(
                synopsis = string(best, "overview")?.takeIf { it.isNotBlank() },
                releaseYear = parsed.first,
                releaseMonth = parsed.second,
                imageUrl = posterPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500" + it },
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
