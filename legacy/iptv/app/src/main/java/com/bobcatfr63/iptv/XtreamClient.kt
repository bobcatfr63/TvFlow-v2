package com.bobcatfr63.iptv

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "xtream_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(credentials: Credentials) {
        prefs.edit()
            .putString("server", credentials.server)
            .putString("username", credentials.username)
            .putString("password", credentials.password)
            .apply()
    }

    fun load(): Credentials? {
        val server = prefs.getString("server", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        return Credentials(server, username, password)
    }

    fun clear() = prefs.edit().clear().apply()
}

data class ServerCategorySelection(
    val live: List<Category>,
    val movies: List<Category>,
    val series: List<Category>,
    val selectedLiveIds: Set<String>,
    val selectedMovieIds: Set<String>,
    val selectedSeriesIds: Set<String>,
    val configured: Boolean
)

class XtreamClient(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private var preferredBase: String? = null
    private val routePrefs = context.getSharedPreferences("fastest_server_route_v1", Context.MODE_PRIVATE)
    @Volatile private var liveFrenchIds: Set<String>? = null
    private val catalogCache = XtreamCatalogCache(context)

    private fun cacheTtlMs(action: String?): Long? = when (action) {
        "get_live_categories", "get_vod_categories", "get_series_categories" -> 6L * 60L * 60L * 1000L
        "get_vod_streams", "get_series" -> 30L * 60L * 1000L
        "get_series_info" -> 6L * 60L * 60L * 1000L
        else -> null
    }

    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "xtream_http"), 64L * 1024L * 1024L))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun normalizeServer(server: String): String {
        val value = server.trim().trimEnd('/')
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "http://$value"
    }

    private fun candidateBases(server: String): List<String> {
        val normalized = normalizeServer(server)
        val saved = routePrefs.getString(normalized, null)
        val host = runCatching { normalized.toHttpUrl().host }.getOrNull()
        val alternate = when (host) {
            "vpn.dndnscloud.ru" -> normalized.replace("vpn.dndnscloud.ru", "line.dndnscloud.ru")
            "line.dndnscloud.ru" -> normalized.replace("line.dndnscloud.ru", "vpn.dndnscloud.ru")
            else -> null
        }
        return listOfNotNull(preferredBase, saved, normalized, alternate).distinct()
    }

    suspend fun optimizeServer(credentials: Credentials): String? = coroutineScope {
        val normalized = normalizeServer(credentials.server)
        val results = candidateBases(credentials.server).map { base ->
            async(Dispatchers.IO) {
                runCatching {
                    val started = System.nanoTime()
                    client.newCall(request(apiUrl(base, credentials))).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        response.body?.source()?.request(1)
                        base to ((System.nanoTime() - started) / 1_000_000L)
                    }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().minByOrNull { it.second }
        results?.first?.also { fastest ->
            preferredBase = fastest
            routePrefs.edit().putString(normalized, fastest).apply()
        }
    }

    private fun apiUrl(base: String, credentials: Credentials, action: String? = null, extra: Pair<String, String>? = null): String {
        val builder = (base + "/player_api.php").toHttpUrl().newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
        if (!action.isNullOrBlank()) builder.addQueryParameter("action", action)
        if (extra != null) builder.addQueryParameter(extra.first, extra.second)
        return builder.build().toString()
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", "IPTVSmartersPlayer/1.1 Android")
        .header("Accept", "application/json,text/plain,*/*")
        .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
        .header("Connection", "keep-alive")
        .get()
        .build()

    private fun httpFailureMessage(code: Int, host: String): String = when (code) {
        401, 403, 511 ->
            "Authentification refusée par $host (HTTP $code). " +
                "Vérifiez l’identifiant, le mot de passe, l’abonnement et le port du serveur."
        else -> "Le serveur $host a renvoyé HTTP $code."
    }

    private fun validateApiBody(body: String, contentType: String?, host: String): String {
        val trimmed = body.trimStart()
        if (trimmed.isBlank()) throw IllegalStateException("Réponse Xtream vide via $host")

        val looksHtml = contentType?.contains("text/html", ignoreCase = true) == true ||
            trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.startsWith("<")

        if (looksHtml) {
            throw IllegalStateException("Le portail $host a renvoyé une page web au lieu de l’API Xtream")
        }

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw IllegalStateException("Réponse Xtream invalide via $host")
        }

        runCatching { json.parseToJsonElement(trimmed) }.getOrElse {
            throw IllegalStateException("Réponse JSON Xtream invalide via $host")
        }
        return trimmed
    }

    private suspend fun raw(
        credentials: Credentials,
        action: String? = null,
        extra: Pair<String, String>? = null
    ): String = withContext(Dispatchers.IO) {
        val ttl = cacheTtlMs(action)
        val cacheKey = action?.takeIf { ttl != null }?.let {
            XtreamCatalogCache.key(normalizeServer(credentials.server), credentials.username, it, extra)
        }

        if (cacheKey != null && ttl != null) {
            catalogCache.get(cacheKey, ttl)?.let { return@withContext it }
        }

        var lastError: Throwable? = null
        val attemptErrors = mutableListOf<String>()
        for (base in candidateBases(credentials.server)) {
            try {
                val url = apiUrl(base, credentials, action, extra)
                client.newCall(request(url)).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException(
                            httpFailureMessage(response.code, response.request.url.host)
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val validBody = validateApiBody(body, response.header("Content-Type"), response.request.url.host)
                    preferredBase = base
                    if (cacheKey != null && action != null) {
                        runCatching { catalogCache.put(cacheKey, action, validBody) }
                    }
                    return@withContext validBody
                }
            } catch (t: Throwable) {
                lastError = t
                val host = runCatching { base.toHttpUrl().host }.getOrDefault(base)
                val detail = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                attemptErrors += if (detail.contains(host, ignoreCase = true)) detail else "$host : $detail"
                if (preferredBase == base) preferredBase = null
            }
        }

        if (cacheKey != null && ttl != null) {
            catalogCache.get(cacheKey, ttl, allowStale = true)?.let { return@withContext it }
        }

        val attempts = attemptErrors.distinct().joinToString("\n") { "• $it" }
        val cacheSuffix = if (cacheKey != null) " Aucun cache local n’est disponible." else ""
        throw IllegalStateException(
            "Connexion Xtream impossible.$cacheSuffix\n$attempts",
            lastError
        )
    }

    private suspend inline fun <reified T> get(
        credentials: Credentials,
        action: String? = null,
        extra: Pair<String, String>? = null
    ): T = json.decodeFromString(raw(credentials, action, extra))

    private fun JsonObject.text(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.int(key: String, default: Int = 0): Int {
        val primitive = this[key]?.jsonPrimitive ?: return default
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: default
    }

    private fun JsonObject.long(key: String, default: Long = 0L): Long =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: default

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull ?: this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

    private suspend fun objects(
        credentials: Credentials,
        action: String,
        extra: Pair<String, String>? = null
    ): List<JsonObject> {
        val element = json.parseToJsonElement(raw(credentials, action, extra))
        return runCatching {
            element.jsonArray.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        }.getOrElse { emptyList() }
    }

    private suspend fun objectsByCategories(
        credentials: Credentials,
        action: String,
        categoryIds: Set<String>
    ): List<JsonObject> {
        if (categoryIds.isEmpty()) return emptyList()

        val result = mutableListOf<JsonObject>()
        for (chunk in categoryIds.chunked(4)) {
            val part = coroutineScope {
                chunk.map { categoryId ->
                    async {
                        runCatching {
                            objects(credentials, action, "category_id" to categoryId)
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
            result += part
        }

        if (result.isNotEmpty()) return result.distinctBy {
            it.text("stream_id").ifBlank { it.text("series_id") }
        }

        return objects(credentials, action).filter { it.text("category_id") in categoryIds }
    }

    private suspend fun categoryList(credentials: Credentials, action: String): List<Category> =
        objects(credentials, action).mapNotNull { obj ->
            val id = obj.text("category_id")
            if (id.isBlank()) null else Category(id, obj.text("category_name"), obj.int("parent_id"))
        }

    private val selectionPrefs = context.getSharedPreferences("server_category_selection_v1", 0)
    @Volatile private var activeSelectionKey: String? = null
    @Volatile private var rawLiveCategories: List<Category>? = null
    @Volatile private var rawMovieCategories: List<Category>? = null
    @Volatile private var rawSeriesCategories: List<Category>? = null

    private fun selectionServerKey(credentials: Credentials): String {
        val host = runCatching { normalizeServer(credentials.server).toHttpUrl().host }.getOrNull()
            ?: credentials.server.trim().lowercase(Locale.ROOT)
        return host.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun configuredKey(serverKey: String) = "configured_$serverKey"
    private fun idsKey(serverKey: String, type: String) = "${type}_$serverKey"

    fun hasSavedCategorySelection(credentials: Credentials): Boolean {
        val key = selectionServerKey(credentials)
        return selectionPrefs.getBoolean(configuredKey(key), false)
    }

    private fun savedIds(credentials: Credentials, type: String): Set<String> {
        val key = selectionServerKey(credentials)
        return selectionPrefs.getStringSet(idsKey(key, type), emptySet())?.toSet().orEmpty()
    }

    suspend fun loadCategorySelection(credentials: Credentials): ServerCategorySelection {
        val key = selectionServerKey(credentials)
        activeSelectionKey = key
        val lists = coroutineScope {
            listOf(
                async { runCatching { categoryList(credentials, "get_live_categories") }.getOrDefault(emptyList()) },
                async { runCatching { categoryList(credentials, "get_vod_categories") }.getOrDefault(emptyList()) },
                async { runCatching { categoryList(credentials, "get_series_categories") }.getOrDefault(emptyList()) }
            ).awaitAll()
        }
        rawLiveCategories = lists[0]
        rawMovieCategories = lists[1]
        rawSeriesCategories = lists[2]
        liveFrenchIds = null
        return ServerCategorySelection(
            live = lists[0],
            movies = lists[1],
            series = lists[2],
            selectedLiveIds = savedIds(credentials, "live"),
            selectedMovieIds = savedIds(credentials, "movies"),
            selectedSeriesIds = savedIds(credentials, "series"),
            configured = selectionPrefs.getBoolean(configuredKey(key), false)
        )
    }

    fun saveCategorySelection(
        credentials: Credentials,
        liveIds: Set<String>,
        movieIds: Set<String>,
        seriesIds: Set<String>
    ) {
        val key = selectionServerKey(credentials)
        activeSelectionKey = key
        selectionPrefs.edit()
            .putStringSet(idsKey(key, "live"), liveIds)
            .putStringSet(idsKey(key, "movies"), movieIds)
            .putStringSet(idsKey(key, "series"), seriesIds)
            .putBoolean(configuredKey(key), true)
            .apply()
        liveFrenchIds = null
    }

    private suspend fun selectedCategories(
        credentials: Credentials,
        type: String,
        action: String,
        cached: List<Category>?
    ): List<Category> {
        val raw = cached ?: categoryList(credentials, action)
        val ids = savedIds(credentials, type)
        return raw.filter { it.id in ids }
    }

    private suspend fun liveIds(credentials: Credentials): Set<String> {
        liveFrenchIds?.let { return it }
        val ids = selectedCategories(credentials, "live", "get_live_categories", rawLiveCategories).map { it.id }.toSet()
        liveFrenchIds = ids
        return ids
    }

    suspend fun authenticate(credentials: Credentials): AuthResponse = get(credentials)

    suspend fun liveCategories(credentials: Credentials): List<Category> {
        val categories = selectedCategories(credentials, "live", "get_live_categories", rawLiveCategories)
        liveFrenchIds = categories.map { it.id }.toSet()
        return categories
    }

    suspend fun liveStreams(credentials: Credentials): List<LiveStream> =
        objectsByCategories(credentials, "get_live_streams", liveIds(credentials)).mapNotNull(::parseLive)

    suspend fun vodCategories(credentials: Credentials): List<Category> =
        selectedCategories(credentials, "movies", "get_vod_categories", rawMovieCategories)

    suspend fun vodStreams(credentials: Credentials, categoryId: String? = null): List<VodStream> =
        objects(credentials, "get_vod_streams", categoryId?.let { "category_id" to it }).mapNotNull(::parseVod)

    suspend fun seriesCategories(credentials: Credentials): List<Category> =
        selectedCategories(credentials, "series", "get_series_categories", rawSeriesCategories)

    suspend fun series(credentials: Credentials, categoryId: String? = null): List<SeriesItem> =
        objects(credentials, "get_series", categoryId?.let { "category_id" to it }).mapNotNull(::parseSeries)

    suspend fun seriesInfo(credentials: Credentials, seriesId: Int): SeriesInfoResponse =
        get(credentials, "get_series_info", "series_id" to seriesId.toString())

    suspend fun shortEpg(credentials: Credentials, streamId: Int, limit: Int = 4): List<EpgItem> = withContext(Dispatchers.IO) {
        val first = runCatching {
            parseEpg(raw(credentials, "get_short_epg", "stream_id" to streamId.toString()))
        }.getOrDefault(emptyList())
        if (first.isNotEmpty()) return@withContext first.take(limit)

        val second = runCatching {
            val base = candidateBases(credentials.server).first()
            val builder = (base + "/player_api.php").toHttpUrl().newBuilder()
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .addQueryParameter("action", "get_short_epg")
                .addQueryParameter("stream_id", streamId.toString())
                .addQueryParameter("limit", limit.toString())
            client.newCall(request(builder.build().toString())).execute().use { response ->
                if (!response.isSuccessful) emptyList() else parseEpg(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
        second.take(limit)
    }

    private fun parseEpg(body: String): List<EpgItem> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val listings = root["epg_listings"]?.jsonArray ?: return emptyList()
        return listings.mapNotNull { node ->
            val obj = runCatching { node.jsonObject }.getOrNull() ?: return@mapNotNull null
            val start = parseEpoch(obj.text("start_timestamp").ifBlank { obj.text("start") })
            val end = parseEpoch(obj.text("stop_timestamp").ifBlank { obj.text("end") })
            if (start <= 0L || end <= 0L) return@mapNotNull null
            EpgItem(
                title = decodeMaybeBase64(obj.text("title")),
                description = decodeMaybeBase64(obj.text("description")),
                startTimestamp = start,
                endTimestamp = end
            )
        }.sortedBy { it.startTimestamp }
    }

    private fun decodeMaybeBase64(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8).trim().ifBlank { value }
        }.getOrDefault(value)
    }

    private fun parseEpoch(value: String): Long {
        val number = value.trim().toLongOrNull() ?: return 0L
        return if (number > 10_000_000_000L) number else number * 1000L
    }

    private fun parseLive(obj: JsonObject): LiveStream? {
        val id = obj.int("stream_id")
        if (id == 0) return null
        return LiveStream(
            id = id,
            name = obj.text("name"),
            categoryId = obj.text("category_id"),
            icon = obj.text("stream_icon").ifBlank { null },
            epgChannelId = obj.text("epg_channel_id").ifBlank { null },
            tvArchive = obj.int("tv_archive"),
            tvArchiveDuration = obj.int("tv_archive_duration")
        )
    }

    private fun parseVod(obj: JsonObject): VodStream? {
        val id = obj.int("stream_id")
        if (id == 0) return null
        return VodStream(
            id = id,
            name = obj.text("name"),
            categoryId = obj.text("category_id"),
            extension = obj.text("container_extension", "mp4"),
            icon = obj.text("stream_icon").ifBlank { null },
            rating = obj.text("rating").ifBlank { null },
            rating5 = obj.double("rating_5based"),
            added = obj.text("added").ifBlank { null },
            releaseDate = obj.text("release_date").ifBlank { obj.text("releaseDate").ifBlank { null } },
            year = obj.text("year").ifBlank { null }
        )
    }

    private fun parseSeries(obj: JsonObject): SeriesItem? {
        val id = obj.int("series_id")
        if (id == 0) return null
        return SeriesItem(
            id = id,
            name = obj.text("name"),
            categoryId = obj.text("category_id"),
            cover = obj.text("cover").ifBlank { obj.text("stream_icon").ifBlank { null } },
            rating = obj.text("rating").ifBlank { null },
            rating5 = obj.double("rating_5based"),
            lastModified = obj.text("last_modified").ifBlank { null },
            releaseDate = obj.text("release_date").ifBlank { obj.text("releaseDate").ifBlank { null } },
            year = obj.text("year").ifBlank { null }
        )
    }

    fun liveUrl(credentials: Credentials, streamId: Int): String =
        "${preferredBase ?: normalizeServer(credentials.server)}/live/${credentials.username}/${credentials.password}/$streamId.ts"

    fun movieUrl(credentials: Credentials, streamId: Int, extension: String): String =
        "${preferredBase ?: normalizeServer(credentials.server)}/movie/${credentials.username}/${credentials.password}/$streamId.$extension"

    fun episodeUrl(credentials: Credentials, episodeId: String, extension: String): String =
        "${preferredBase ?: normalizeServer(credentials.server)}/series/${credentials.username}/${credentials.password}/$episodeId.$extension"
}
