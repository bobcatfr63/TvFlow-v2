package com.tvflow.app.data.repository

import android.util.Base64
import android.util.Log
import com.tvflow.app.data.local.CredentialsStore
import com.tvflow.app.data.local.EpgProgramEntity
import com.tvflow.app.data.local.LiveCategoryEntity
import com.tvflow.app.data.local.LiveChannelEntity
import com.tvflow.app.data.local.MediaCategoryEntity
import com.tvflow.app.data.local.PlaybackPositionEntity
import com.tvflow.app.data.local.SeriesItemEntity
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.data.local.TVFlowDao
import com.tvflow.app.data.local.VodItemEntity
import com.tvflow.app.data.local.WatchHistoryEntity
import com.tvflow.app.data.metadata.TmdbMetadataClient
import com.tvflow.app.data.remote.XtreamApi
import com.tvflow.app.domain.ConnectionDiagnostics
import com.tvflow.app.domain.ConnectionProbe
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.Episode
import com.tvflow.app.domain.LiveCategory
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.LiveSubFilter
import com.tvflow.app.domain.MediaCategory
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.PlaybackPosition
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.ServerProfile
import com.tvflow.app.domain.StreamHealth
import com.tvflow.app.domain.VodItem
import com.tvflow.app.domain.WatchHistoryItem
import com.tvflow.app.domain.XtreamCredentials
import com.tvflow.app.domain.parseXtreamM3uUrl
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import retrofit2.HttpException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val GROUP_CATEGORY_PREFIX = "__group__:"
private const val FRANCE_CATEGORY_PREFIX = "__france__:"
private const val KIDS_CATEGORY_PREFIX = "__kids__:"
private const val OTHER_CATEGORY_PREFIX = "__other__:"
private val PROVIDER_CATEGORY_REGEX = Regex("^\\|([^|]+)\\|\\s*(.+)$")
private val CATEGORY_DIACRITICS = Regex("\\p{M}+")
private val KIDS_CATEGORY_MARKERS = listOf(
    "ENFANT", "KIDS", "KID ", "CHILD", "NINOS", "BAMBINI",
    "KINDER", "KINDEREN", "CRIANC", "DZIECI", "ΠΑΙΔ"
)

private val STRONG_FRENCH_CATEGORY_MARKERS = setOf(
    "FRANCE", "FRANCAIS", "FRANCAISE", "FRENCH", "VOSTFR", "VF"
)
private val FRENCH_CATEGORY_CODES = setOf(
    "FR", "FRA", "FRE"
)
private val FOREIGN_LANGUAGE_CODES = setOf(
    "EN", "ENG", "ENGLISH", "UK", "US",
    "ES", "ESP", "SPANISH", "ESPANOL",
    "DE", "GER", "GERMAN", "DEUTSCH",
    "IT", "ITA", "ITALIAN", "ITALIANO",
    "PT", "POR", "PORTUGUESE",
    "NL", "DUTCH", "NEDERLANDS",
    "AR", "ARA", "ARABIC",
    "TR", "TUR", "TURKISH",
    "RU", "RUS", "RUSSIAN"
)
private val FRENCH_AUDIO_HINTS = setOf(
    "MULTI", "AUDIO", "DUB", "DUBBED", "DOUBLAGE"
)

data class GlobalSearchResults(
    val live: List<LiveChannel> = emptyList(),
    val vod: List<VodItem> = emptyList(),
    val series: List<SeriesItem> = emptyList()
)

private data class SearchCache(
    val live: List<Pair<String, LiveChannel>>,
    val vod: List<Pair<String, VodItem>>,
    val series: List<Pair<String, SeriesItem>>
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class XtreamRepository @Inject constructor(
    private val api: XtreamApi,
    private val dao: TVFlowDao,
    private val credentialsStore: CredentialsStore,
    private val settingsStore: SettingsStore,
    private val okHttpClient: OkHttpClient,
    private val tmdbMetadataClient: TmdbMetadataClient
) {
    val savedCredentials = credentialsStore.credentials
    val profiles = credentialsStore.profiles
    val activeProfile = credentialsStore.activeProfile
    val activeProfileId = credentialsStore.activeProfileId

    private val liveResultCache = object : LinkedHashMap<String, List<LiveChannel>>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LiveChannel>>?): Boolean = size > 12
    }
    private val searchCache = mutableMapOf<String, SearchCache>()
    private val searchCacheMutex = Mutex()
    private val xmltvMutex = Mutex()
    private val catchupProbeCache = LinkedHashMap<String, Boolean>(32, 0.75f, true)
    @Volatile private var lastXmltvRefreshAtMs = 0L
    @Volatile private var lastXmltvAttemptAtMs = 0L

    suspend fun login(credentials: XtreamCredentials, displayName: String? = null) {
        val source = parseXtreamM3uUrl(credentials.serverUrl) ?: credentials
        val normalized = source.copy(
            serverUrl = normalizeServer(source.serverUrl),
            outputFormat = source.outputFormat.lowercase().takeIf { it in setOf("ts", "m3u8") } ?: "ts"
        )
        authenticate(normalized)
        credentialsStore.save(normalized, displayName)
        val profileId = currentProfileId()
        refreshCatalog(normalized, profileId)
        runCatching { refreshMediaCatalog(normalized, profileId) }
    }

    suspend fun switchProfile(profileId: String) {
        credentialsStore.switchProfile(profileId)
        refreshCatalog()
        runCatching { refreshMediaCatalog() }
    }

    suspend fun deleteProfile(profileId: String) {
        val wasActive = activeProfileId.first() == profileId
        dao.deleteProfileData(profileId)
        credentialsStore.deleteProfile(profileId)
        if (wasActive && savedCredentials.first() != null) {
            refreshCatalog()
            runCatching { refreshMediaCatalog() }
        }
    }

    suspend fun refreshCatalog() {
        val credentials = savedCredentials.first() ?: error("Aucun serveur IPTV configuré")
        refreshCatalog(credentials, currentProfileId())
    }

    suspend fun refreshMediaCatalog() {
        val credentials = savedCredentials.first() ?: error("Aucun serveur IPTV configuré")
        refreshMediaCatalog(credentials, currentProfileId())
    }

    suspend fun refreshAllWithProgress(onProgress: (Int, String) -> Unit) = coroutineScope {
        val credentials = savedCredentials.first() ?: error("Aucun serveur IPTV configuré")
        val profileId = currentProfileId()
        val apiUrl = playerApi(credentials.serverUrl)

        onProgress(5, "Connexion au serveur")
        authenticate(credentials)
        onProgress(12, "Connexion validée")

        val liveCategories = api.getLiveCategories(apiUrl, credentials.username, credentials.password)
        onProgress(24, "Catégories TV reçues")
        val liveStreams = api.getLiveStreams(apiUrl, credentials.username, credentials.password)
        onProgress(40, "Chaînes en direct reçues")
        dao.replaceCatalog(
            profileId = profileId,
            categories = liveCategories.mapIndexed { index, dto ->
                LiveCategoryEntity(profileId, dto.categoryId, dto.categoryName, index)
            },
            channels = liveStreams.map { dto ->
                LiveChannelEntity(
                    profileId = profileId,
                    streamId = dto.streamId,
                    name = dto.name,
                    categoryId = dto.categoryId.orEmpty(),
                    logoUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                    epgChannelId = dto.epgChannelId?.takeIf { it.isNotBlank() },
                    streamType = dto.streamType ?: "live",
                    tvArchive = dto.tvArchive == 1,
                    tvArchiveDuration = dto.tvArchiveDuration ?: 0
                )
            }
        )
        onProgress(48, "Direct enregistré")

        val vodCategoriesDeferred = async {
            api.getVodCategories(apiUrl, credentials.username, credentials.password)
        }
        val seriesCategoriesDeferred = async {
            api.getSeriesCategories(apiUrl, credentials.username, credentials.password)
        }
        val vodDeferred = async {
            api.getVodStreams(apiUrl, credentials.username, credentials.password)
        }
        val seriesDeferred = async {
            api.getSeries(apiUrl, credentials.username, credentials.password)
        }

        val vodCategories = vodCategoriesDeferred.await()
        onProgress(58, "Catégories films reçues")
        val seriesCategories = seriesCategoriesDeferred.await()
        onProgress(66, "Catégories séries reçues")
        val vod = vodDeferred.await()
        onProgress(80, "Films reçus")
        val series = seriesDeferred.await()
        onProgress(92, "Séries reçues")

        dao.replaceMediaCatalog(
            profileId = profileId,
            vodCategories = vodCategories.mapIndexed { index, dto ->
                MediaCategoryEntity(profileId, dto.categoryId, dto.categoryName, MediaType.VOD.name, index)
            },
            seriesCategories = seriesCategories.mapIndexed { index, dto ->
                MediaCategoryEntity(profileId, dto.categoryId, dto.categoryName, MediaType.SERIES.name, index)
            },
            vod = vod.map { dto ->
                run {
                    val (releaseYear, releaseMonth) = parseReleaseYearMonth(dto.releaseDate, dto.year)
                    VodItemEntity(
                        profileId = profileId,
                        streamId = dto.streamId,
                        name = dto.name,
                        categoryId = dto.categoryId.orEmpty(),
                        posterUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                        rating = dto.rating,
                        added = dto.added?.toLongOrNull(),
                        synopsis = null,
                        releaseYear = releaseYear,
                        releaseMonth = releaseMonth,
                        containerExtension = dto.containerExtension
                    )
                }
            },
            series = series.map { dto ->
                run {
                    val (releaseYear, releaseMonth) = parseReleaseYearMonth(dto.releaseDate, dto.year)
                    SeriesItemEntity(profileId, dto.seriesId, dto.name, dto.categoryId.orEmpty(), dto.cover?.takeIf { it.isNotBlank() }, dto.rating, dto.plot, releaseYear, releaseMonth)
                }
            }
        )
        invalidateSearchCache(profileId)
        onProgress(100, "Actualisation terminée")
    }

    private suspend fun authenticate(credentials: XtreamCredentials) {
        val response = api.authenticate(playerApi(credentials.serverUrl), credentials.username, credentials.password)
        val authenticated = response.userInfo?.auth == 1 || response.userInfo?.status.equals("Active", true)
        if (!authenticated) error(response.userInfo?.message ?: "Identifiants Xtream refusés")
    }

    private suspend fun refreshCatalog(credentials: XtreamCredentials, profileId: String) {
        val apiUrl = playerApi(credentials.serverUrl)
        val categories = api.getLiveCategories(apiUrl, credentials.username, credentials.password)
        val channels = api.getLiveStreams(apiUrl, credentials.username, credentials.password)
        dao.replaceCatalog(
            profileId = profileId,
            categories = categories.mapIndexed { index, dto ->
                LiveCategoryEntity(profileId, dto.categoryId, dto.categoryName, index)
            },
            channels = channels.map { dto ->
                LiveChannelEntity(
                    profileId = profileId,
                    streamId = dto.streamId,
                    name = dto.name,
                    categoryId = dto.categoryId.orEmpty(),
                    logoUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                    epgChannelId = dto.epgChannelId?.takeIf { it.isNotBlank() },
                    streamType = dto.streamType ?: "live",
                    tvArchive = dto.tvArchive == 1,
                    tvArchiveDuration = dto.tvArchiveDuration ?: 0
                )
            }
        )
        invalidateSearchCache(profileId)
    }

    private suspend fun refreshMediaCatalog(credentials: XtreamCredentials, profileId: String) = coroutineScope {
        val apiUrl = playerApi(credentials.serverUrl)
        val vodCategoriesDeferred = async { api.getVodCategories(apiUrl, credentials.username, credentials.password) }
        val seriesCategoriesDeferred = async { api.getSeriesCategories(apiUrl, credentials.username, credentials.password) }
        val vodDeferred = async { api.getVodStreams(apiUrl, credentials.username, credentials.password) }
        val seriesDeferred = async { api.getSeries(apiUrl, credentials.username, credentials.password) }

        val vodCategories = vodCategoriesDeferred.await()
        val seriesCategories = seriesCategoriesDeferred.await()
        val vod = vodDeferred.await()
        val series = seriesDeferred.await()

        dao.replaceMediaCatalog(
            profileId = profileId,
            vodCategories = vodCategories.mapIndexed { index, dto ->
                MediaCategoryEntity(profileId, dto.categoryId, dto.categoryName, MediaType.VOD.name, index)
            },
            seriesCategories = seriesCategories.mapIndexed { index, dto ->
                MediaCategoryEntity(profileId, dto.categoryId, dto.categoryName, MediaType.SERIES.name, index)
            },
            vod = vod.map { dto ->
                run {
                    val (releaseYear, releaseMonth) = parseReleaseYearMonth(dto.releaseDate, dto.year)
                    VodItemEntity(
                        profileId = profileId,
                        streamId = dto.streamId,
                        name = dto.name,
                        categoryId = dto.categoryId.orEmpty(),
                        posterUrl = dto.streamIcon?.takeIf { it.isNotBlank() },
                        rating = dto.rating,
                        added = dto.added?.toLongOrNull(),
                        synopsis = null,
                        releaseYear = releaseYear,
                        releaseMonth = releaseMonth,
                        containerExtension = dto.containerExtension
                    )
                }
            },
            series = series.map { dto ->
                run {
                    val (releaseYear, releaseMonth) = parseReleaseYearMonth(dto.releaseDate, dto.year)
                    SeriesItemEntity(profileId, dto.seriesId, dto.name, dto.categoryId.orEmpty(), dto.cover?.takeIf { it.isNotBlank() }, dto.rating, dto.plot, releaseYear, releaseMonth)
                }
            }
        )
        invalidateSearchCache(profileId)
    }

    fun observeEpg(streamId: Int): Flow<List<EpgProgram>> = activeProfileId.flatMapLatest { profileId ->
        if (profileId == null) flowOf(emptyList()) else dao.observeEpg(profileId, streamId)
    }.map { list -> list.map { EpgProgram(it.streamId, it.startTimestamp, it.stopTimestamp, it.title, it.description, it.hasArchive) } }

    fun observeEpgForStreams(streamIds: List<Int>): Flow<List<EpgProgram>> = activeProfileId.flatMapLatest { profileId ->
        if (profileId == null || streamIds.isEmpty()) flowOf(emptyList()) else dao.observeEpgForStreams(profileId, streamIds)
    }.map { list -> list.map { EpgProgram(it.streamId, it.startTimestamp, it.stopTimestamp, it.title, it.description, it.hasArchive) } }

    suspend fun refreshEpg(streamId: Int, limit: Int = 8) {
        val credentials = savedCredentials.first() ?: return
        val profileId = currentProfileId()
        val response = api.getShortEpg(playerApi(credentials.serverUrl), credentials.username, credentials.password, streamId = streamId, limit = limit)
        Log.d("TVFlowEpg", "stream=$streamId listings=${response.listings.size} first=${response.listings.firstOrNull()?.startTimestamp}/${response.listings.firstOrNull()?.stopTimestamp}")
        val items = response.listings.mapNotNull { dto ->
            val start = parseEpgTimestamp(dto.startTimestamp) ?: return@mapNotNull null
            val stop = parseEpgTimestamp(dto.stopTimestamp) ?: return@mapNotNull null
            if (stop <= start) return@mapNotNull null
            EpgProgramEntity(
                profileId,
                streamId,
                start,
                stop,
                decodeEpgText(dto.title).ifBlank { "Programme sans titre" },
                decodeEpgText(dto.description).takeIf { it.isNotBlank() },
                dto.hasArchive == 1
            )
        }
        if (items.isNotEmpty()) {
            dao.clearEpg(profileId, streamId)
            dao.upsertEpg(items)
            dao.deleteExpiredEpg(profileId, System.currentTimeMillis() / 1000L - 86_400L)
            return
        }

        refreshXmltvFallbackIfNeeded(credentials, profileId)
    }

    fun observeCategories(): Flow<List<LiveCategory>> = activeProfileId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                combine(
                    dao.observeCategories(id),
                    settingsStore.categorySectionOverrides(id)
                ) { categories, overrides ->
                    buildPrimaryLiveCategories(categories, overrides)
                }
            }
        }

    fun observeManageCategories(): Flow<List<LiveCategory>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeManageCategories(id) }
        .map { list -> list.map { LiveCategory(it.id, it.name, it.sortOrder, it.isHidden, it.isLocked) } }

    fun observeChannels(categoryId: String): Flow<List<LiveChannel>> {
        val sourceIds = decodeCategoryGroupId(categoryId)
        val frenchOnly = categoryId.startsWith(FRANCE_CATEGORY_PREFIX)
        return activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(emptyList())
            } else {
                val source = when {
                    sourceIds.size > 1 -> dao.observeChannelsForCategories(profileId, sourceIds)
                    sourceIds.isNotEmpty() -> dao.observeChannels(profileId, sourceIds.first())
                    else -> flowOf(emptyList())
                }
                val canonicalSourceKey = sourceIds.sorted().joinToString(",")
                applyStreamVisibility(
                    source = source,
                    cacheScope = "$profileId|$canonicalSourceKey|fr=$frenchOnly",
                    frenchOnly = frenchOnly
                )
            }
        }
    }
    fun observeManageChannels(categoryId: String): Flow<List<LiveChannel>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeManageChannels(id, categoryId) }.map(::mapChannels)
    fun observeAllChannels(): Flow<List<LiveChannel>> = applyStreamVisibility(activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeAllChannels(id) })
    fun observeChannel(streamId: Int): Flow<LiveChannel?> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeChannel(id, streamId) }.map { it?.toDomain() }
    fun observeFavorites(): Flow<List<LiveChannel>> = applyStreamVisibility(activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeFavorites(id) })
    fun observeRecentChannels(limit: Int = 20): Flow<List<LiveChannel>> = applyStreamVisibility(activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeRecentChannels(id, limit) })
    fun searchLive(query: String): Flow<List<LiveChannel>> = applyStreamVisibility(activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.searchLive(id, query) })

    fun observeMediaCategories(type: MediaType): Flow<List<MediaCategory>> =
        activeProfileId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                combine(
                    dao.observeMediaCategories(id, type.name),
                    settingsStore.categorySectionOverrides(id)
                ) { list, overrides ->
                    list.map {
                        val key = type.name + ":" + it.id
                        val overridden = overrides[key]?.let { value ->
                            runCatching { MediaSection.valueOf(value) }.getOrNull()
                        }
                        MediaCategory(
                            id = it.id,
                            name = it.name,
                            type = MediaType.valueOf(it.type),
                            order = it.sortOrder,
                            section = overridden ?: classifyMediaSection(it.name)
                        )
                    }
                }
            }
        }
    fun observeVod(categoryId: String): Flow<List<VodItem>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeVod(id, categoryId) }.map { list -> list.map { it.toDomain() } }

    fun observeVodItem(streamId: Int): Flow<VodItem?> = activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(null) else dao.observeVodItem(id, streamId)
    }.map { it?.toDomain() }

    suspend fun enrichVodDetails(streamId: Int) {
        val profileId = currentProfileId()
        val item = dao.getVodItem(profileId, streamId) ?: return
        if (!item.synopsis.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        val staleAfterMs = 7L * 24L * 60L * 60L * 1000L
        val shouldQueryProvider = item.detailsFetchedAt == null || now - item.detailsFetchedAt > staleAfterMs

        if (shouldQueryProvider) {
            val credentials = savedCredentials.first()
            if (credentials != null) {
                val response = runCatching {
                    api.getVodInfo(
                        playerApiUrl = playerApi(credentials.serverUrl),
                        username = credentials.username,
                        password = credentials.password,
                        vodId = streamId
                    )
                }.onFailure {
                    Log.w("TVFlowVodInfo", "Xtream get_vod_info failed for id=$streamId", it)
                }.getOrNull()

                if (response != null) {
                    val info = response.info
                    val plot = info?.plot?.trim()?.takeIf { it.isNotBlank() }
                    val (year, month) = parseReleaseYearMonth(info?.releaseDate, info?.year)
                    dao.updateVodMetadata(
                        profileId = profileId,
                        streamId = streamId,
                        synopsis = plot,
                        year = year,
                        month = month,
                        posterUrl = info?.movieImage?.takeIf { it.isNotBlank() },
                        rating = info?.rating?.takeIf { it.isNotBlank() },
                        detailsFetchedAt = now
                    )
                    if (!plot.isNullOrBlank()) {
                        Log.d("TVFlowVodInfo", "Xtream synopsis cached for id=$streamId")
                        return
                    }
                }
            }
        }

        if (!settingsStore.currentTmdbToken().isNullOrBlank()) {
            val latest = dao.getVodItem(profileId, streamId) ?: item
            val metadata = runCatching {
                tmdbMetadataClient.search(MediaType.VOD, latest.name, latest.releaseYear)
            }.onFailure {
                Log.w("TVFlowTmdb", "TMDb VOD detail fallback failed for id=$streamId", it)
            }.getOrNull()
            if (metadata != null) {
                dao.updateVodMetadata(
                    profileId = profileId,
                    streamId = streamId,
                    synopsis = metadata.synopsis,
                    year = metadata.releaseYear,
                    month = metadata.releaseMonth,
                    posterUrl = metadata.imageUrl,
                    rating = metadata.rating,
                    detailsFetchedAt = now
                )
            }
        }
    }

    fun observeRecentVodReleases(limit: Int = 12): Flow<List<VodItem>> =
        activeProfileId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.observeRecentVodReleases(id, limit)
        }.map { list -> list.map { it.toDomain() } }

    fun observeRecentSeriesReleases(limit: Int = 12): Flow<List<SeriesItem>> =
        activeProfileId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.observeRecentSeriesReleases(id, limit)
        }.map { list -> list.map { it.toDomain() } }

    fun observeRecentFrenchVodReleases(limit: Int = 12): Flow<List<VodItem>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(emptyList())
            } else {
                dao.observeMediaCategories(profileId, MediaType.VOD.name).flatMapLatest { categories ->
                    val ids = categories.filter { classifyMediaSection(it.name) == MediaSection.FRANCE }.map { it.id }
                    if (ids.isEmpty()) flowOf(emptyList())
                    else dao.observeRecentVodReleasesForCategories(profileId, ids, limit)
                }
            }
        }.map { list -> list.map { it.toDomain() } }

    fun observeRecentFrenchSeriesReleases(limit: Int = 12): Flow<List<SeriesItem>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(emptyList())
            } else {
                dao.observeMediaCategories(profileId, MediaType.SERIES.name).flatMapLatest { categories ->
                    val ids = categories.filter { classifyMediaSection(it.name) == MediaSection.FRANCE }.map { it.id }
                    if (ids.isEmpty()) flowOf(emptyList())
                    else dao.observeRecentSeriesReleasesForCategories(profileId, ids, limit)
                }
              }
        }.map { list -> list.map { it.toDomain() } }

    suspend fun randomVodPreview(excludeStreamId: Int? = null): Pair<VodItem, String>? {
        val profileId = activeProfileId.filterNotNull().first()
        val frenchCategoryIds = dao.observeMediaCategories(profileId, MediaType.VOD.name)
            .first()
            .filter { classifyMediaSection(it.name) == MediaSection.FRANCE }
            .map { it.id }
        if (frenchCategoryIds.isEmpty()) return null

        val candidates = dao.previewVodCandidates(profileId, frenchCategoryIds, limit = 80)
            .map { it.toDomain() }
            .filter { it.streamId != excludeStreamId }
        if (candidates.isEmpty()) return null

        val item = candidates.random()
        return item to vodUrl(item)
    }
    fun observeSeries(categoryId: String): Flow<List<SeriesItem>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeSeries(id, categoryId) }.map { list -> list.map { it.toDomain() } }

    fun pagedVod(categoryId: String, year: Int? = null, month: Int? = null): Flow<PagingData<VodItem>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 60, prefetchDistance = 20, enablePlaceholders = false),
                    pagingSourceFactory = { dao.pagingVod(profileId, categoryId, year, month) }
                ).flow.map { paging -> paging.map { it.toDomain() } }
            }
        }

    fun pagedSeries(categoryId: String, year: Int? = null, month: Int? = null): Flow<PagingData<SeriesItem>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 60, prefetchDistance = 20, enablePlaceholders = false),
                    pagingSourceFactory = { dao.pagingSeries(profileId, categoryId, year, month) }
                ).flow.map { paging -> paging.map { it.toDomain() } }
            }
        }

    fun observeReleaseYears(type: MediaType, categoryId: String): Flow<List<Int>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) flowOf(emptyList())
            else when (type) {
                MediaType.VOD -> dao.observeVodReleaseYears(profileId, categoryId)
                MediaType.SERIES -> dao.observeSeriesReleaseYears(profileId, categoryId)
            }
        }

    fun observeReleaseMonths(type: MediaType, categoryId: String, year: Int): Flow<List<Int>> =
        activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) flowOf(emptyList())
            else when (type) {
                MediaType.VOD -> dao.observeVodReleaseMonths(profileId, categoryId, year)
                MediaType.SERIES -> dao.observeSeriesReleaseMonths(profileId, categoryId, year)
            }
        }

    suspend fun enrichMissingMediaMetadata(type: MediaType, categoryId: String, limit: Int = 24) {
        val profileId = currentProfileId()
        if (settingsStore.currentTmdbToken().isNullOrBlank()) return
        when (type) {
            MediaType.VOD -> dao.getVodMissingMetadata(profileId, categoryId, limit).forEach { item ->
                val metadata = runCatching {
                    tmdbMetadataClient.search(MediaType.VOD, item.name, item.releaseYear)
                }.getOrNull() ?: return@forEach
                dao.updateVodMetadata(
                    profileId = profileId,
                    streamId = item.streamId,
                    synopsis = metadata.synopsis,
                    year = metadata.releaseYear,
                    month = metadata.releaseMonth,
                    posterUrl = metadata.imageUrl,
                    rating = metadata.rating
                )
                Log.d("TVFlowTmdb", "updated VOD id=${item.streamId} year=${metadata.releaseYear} month=${metadata.releaseMonth} synopsis=${!metadata.synopsis.isNullOrBlank()}")
            }
            MediaType.SERIES -> dao.getSeriesMissingMetadata(profileId, categoryId, limit).forEach { item ->
                val metadata = runCatching {
                    tmdbMetadataClient.search(MediaType.SERIES, item.name, item.releaseYear)
                }.getOrNull() ?: return@forEach
                dao.updateSeriesMetadata(
                    profileId = profileId,
                    seriesId = item.seriesId,
                    plot = metadata.synopsis,
                    year = metadata.releaseYear,
                    month = metadata.releaseMonth,
                    coverUrl = metadata.imageUrl,
                    rating = metadata.rating
                )
            }
        }
    }
    fun searchVod(query: String): Flow<List<VodItem>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.searchVod(id, query) }.map { list -> list.map { it.toDomain() } }
    fun searchSeries(query: String): Flow<List<SeriesItem>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.searchSeries(id, query) }.map { list -> list.map { it.toDomain() } }

    suspend fun searchGlobal(query: String, limitPerType: Int = 60): GlobalSearchResults = withContext(Dispatchers.Default) {
        val profileId = currentProfileId()
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.length < 2) return@withContext GlobalSearchResults()
        val cache = searchCacheMutex.withLock {
            searchCache[profileId] ?: buildSearchCache(profileId).also { searchCache[profileId] = it }
        }
        fun <T> find(source: List<Pair<String, T>>): List<T> =
            source.asSequence()
                .filter { (key, _) -> key.contains(normalizedQuery) }
                .sortedBy { (key, _) -> key.indexOf(normalizedQuery).coerceAtLeast(0) }
                .take(limitPerType)
                .map { it.second }
                .toList()

        GlobalSearchResults(
            live = find(cache.live),
            vod = find(cache.vod),
            series = find(cache.series)
        )
    }

    private suspend fun buildSearchCache(profileId: String): SearchCache = withContext(Dispatchers.IO) {
        SearchCache(
            live = dao.getAllChannels(profileId)
                .asSequence()
                .filterNot { it.isHidden }
                .map { normalizeSearchText(it.name) to it.toDomain() }
                .toList(),
            vod = dao.getAllVod(profileId)
                .map { normalizeSearchText(it.name) to it.toDomain() },
            series = dao.getAllSeries(profileId)
                .map { normalizeSearchText(it.name) to it.toDomain() }
        )
    }

    private suspend fun invalidateSearchCache(profileId: String) {
        searchCacheMutex.withLock { searchCache.remove(profileId) }
    }

    suspend fun getSeriesEpisodes(seriesId: Int): List<Episode> {
        val credentials = savedCredentials.first() ?: return emptyList()
        val response = api.getSeriesInfo(playerApi(credentials.serverUrl), credentials.username, credentials.password, seriesId = seriesId)
        return response.episodes.entries.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }.flatMap { (seasonKey, episodes) ->
            val season = seasonKey.toIntOrNull() ?: 0
            episodes.mapIndexed { index, dto ->
                Episode(dto.id, season, dto.episodeNumber ?: index + 1, dto.title?.ifBlank { null } ?: "Épisode ${dto.episodeNumber ?: index + 1}", dto.containerExtension, dto.info?.movieImage, dto.info?.plot)
            }
        }
    }

    suspend fun toggleFavorite(streamId: Int) {
        val profileId = currentProfileId()
        dao.toggleFavorite(profileId, streamId)
        settingsStore.markFavoriteSyncChanged(profileId)
    }

    fun observeFavoriteIds(): Flow<Set<Int>> = activeProfileId.flatMapLatest { profileId ->
        if (profileId == null) flowOf(emptySet())
        else dao.observeFavorites(profileId).map { list -> list.map { it.streamId }.toSet() }
    }

    suspend fun applySyncedFavorites(profileId: String, streamIds: Set<Int>, timestamp: Long): Boolean {
        val localTimestamp = settingsStore.favoriteSyncTimestamp(profileId).first()
        if (timestamp <= localTimestamp) return false
        dao.replaceFavorites(profileId, streamIds.toList())
        settingsStore.markFavoriteSyncChanged(profileId, timestamp)
        return true
    }

    suspend fun currentFavoriteSyncTimestamp(profileId: String): Long =
        settingsStore.favoriteSyncTimestamp(profileId).first()

    suspend fun setCategorySectionOverride(categoryId: String, section: MediaSection?) {
        val profileId = currentProfileId()
        settingsStore.setCategorySectionOverride(
            profileId = profileId,
            categoryKey = "LIVE:" + categoryId,
            section = section?.name
        )
    }

    suspend fun setMediaCategorySectionOverride(
        type: MediaType,
        categoryId: String,
        section: MediaSection?
    ) {
        val profileId = currentProfileId()
        settingsStore.setCategorySectionOverride(
            profileId = profileId,
            categoryKey = type.name + ":" + categoryId,
            section = section?.name
        )
    }

    suspend fun markWatched(streamId: Int) = dao.markWatched(currentProfileId(), streamId, System.currentTimeMillis())

    suspend fun reportPlaybackReady(streamId: Int) {
        dao.updateStreamHealth(
            profileId = currentProfileId(),
            streamId = streamId,
            status = StreamHealth.ONLINE.name,
            checkedAt = System.currentTimeMillis(),
            failureCount = 0,
            httpCode = null,
            error = null
        )
    }

    suspend fun reportPlaybackFailure(streamId: Int, httpCode: Int?, error: String?) {
        val profileId = currentProfileId()
        val current = dao.getChannel(profileId, streamId) ?: return
        val failures = (current.healthFailureCount + 1).coerceAtMost(99)
        val normalizedError = error?.take(180)
        val status = classifyStreamHealth(httpCode, normalizedError)
        dao.updateStreamHealth(
            profileId = profileId,
            streamId = streamId,
            status = status.name,
            checkedAt = System.currentTimeMillis(),
            failureCount = failures,
            httpCode = httpCode,
            error = normalizedError
        )
    }
    suspend fun setChannelHidden(streamId: Int, hidden: Boolean) = dao.setChannelHidden(currentProfileId(), streamId, hidden)
    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean) = dao.setCategoryHidden(currentProfileId(), categoryId, hidden)
    suspend fun setCategoryLocked(categoryId: String, locked: Boolean) = dao.setCategoryLocked(currentProfileId(), categoryId, locked)

    suspend fun moveCategory(categoryId: String, delta: Int) {
        val profileId = currentProfileId()
        val list = dao.observeManageCategories(profileId).first()
        val index = list.indexOfFirst { it.id == categoryId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return
        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { order, category -> dao.setCategoryOrder(profileId, category.id, order) }
    }

    suspend fun moveChannel(categoryId: String, streamId: Int, delta: Int) {
        val profileId = currentProfileId()
        val list = dao.observeManageChannels(profileId, categoryId).first()
        val index = list.indexOfFirst { it.streamId == streamId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return
        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { order, channel -> dao.setChannelOrder(profileId, channel.streamId, order) }
    }

    fun streamUrl(credentials: XtreamCredentials, channel: LiveChannel): String =
        buildLiveStreamUrl(credentials, channel.streamId)

    suspend fun resolveCatchupUrl(credentials: XtreamCredentials, channel: LiveChannel, program: EpgProgram): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000L
        val archiveDays = channel.tvArchiveDuration.coerceAtLeast(0)
        if (archiveDays > 0 && program.startTimestamp < now - TimeUnit.DAYS.toSeconds(archiveDays.toLong())) return@withContext null

        val url = buildCatchupStreamUrl(credentials, channel.streamId, program.startTimestamp, program.stopTimestamp)
        if (channel.tvArchive || program.hasArchive) return@withContext url

        val cacheKey = "${channel.streamId}:${program.startTimestamp}"
        synchronized(catchupProbeCache) { catchupProbeCache[cacheKey] }?.let { supported ->
            return@withContext url.takeIf { supported }
        }

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
            .build()
        val supported = runCatching {
            okHttpClient.newBuilder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
        synchronized(catchupProbeCache) {
            catchupProbeCache[cacheKey] = supported
            while (catchupProbeCache.size > 32) catchupProbeCache.remove(catchupProbeCache.entries.first().key)
        }
        url.takeIf { supported }
    }

    suspend fun vodUrl(item: VodItem): String {
        val credentials = savedCredentials.first() ?: error("Aucun serveur IPTV configuré")
        return buildVodStreamUrl(credentials, item.streamId, item.containerExtension)
    }

    suspend fun episodeUrl(episode: Episode): String {
        val credentials = savedCredentials.first() ?: error("Aucun serveur IPTV configuré")
        return buildEpisodeStreamUrl(credentials, episode.id, episode.containerExtension)
    }

    suspend fun savePlaybackPosition(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        title: String,
        imageUrl: String?,
        kind: String,
        mediaId: String,
        containerExtension: String?,
        seriesId: Int? = null
    ) {
        val profileId = currentProfileId()
        if (positionMs < 5_000L || durationMs <= 0L) return
        val now = System.currentTimeMillis()
        val completed = durationMs - positionMs < 30_000L || positionMs >= durationMs * 0.95
        dao.upsertWatchHistory(
            WatchHistoryEntity(
                profileId = profileId,
                mediaKey = mediaKey,
                kind = kind,
                mediaId = mediaId,
                seriesId = seriesId,
                title = title,
                imageUrl = imageUrl,
                containerExtension = containerExtension,
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = now,
                completed = completed
            )
        )
        if (completed) {
            dao.clearPlaybackPosition(profileId, mediaKey)
        } else {
            dao.savePlaybackPosition(
                PlaybackPositionEntity(
                    profileId = profileId,
                    mediaKey = mediaKey,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = now,
                    title = title,
                    imageUrl = imageUrl,
                    kind = kind,
                    mediaId = mediaId,
                    containerExtension = containerExtension,
                    seriesId = seriesId
                )
            )
        }
    }

    suspend fun markPlaybackCompleted(
        mediaKey: String,
        title: String,
        imageUrl: String?,
        kind: String,
        mediaId: String,
        containerExtension: String?,
        seriesId: Int?,
        durationMs: Long
    ) {
        val profileId = currentProfileId()
        val existing = dao.getPlaybackPosition(profileId, mediaKey)
        dao.upsertWatchHistory(
            WatchHistoryEntity(
                profileId = profileId,
                mediaKey = mediaKey,
                kind = kind,
                mediaId = mediaId,
                seriesId = seriesId,
                title = title,
                imageUrl = imageUrl,
                containerExtension = containerExtension,
                positionMs = durationMs.coerceAtLeast(existing?.positionMs ?: 0L),
                durationMs = durationMs.coerceAtLeast(existing?.durationMs ?: 0L),
                watchedAt = System.currentTimeMillis(),
                completed = true
            )
        )
        dao.clearPlaybackPosition(profileId, mediaKey)
    }

    suspend fun playbackPosition(mediaKey: String): PlaybackPosition? = dao.getPlaybackPosition(currentProfileId(), mediaKey)?.toDomain()
    fun observeContinueWatching(limit: Int = 20): Flow<List<PlaybackPosition>> = activeProfileId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observePlaybackPositions(id, limit) }.map { list -> list.map { it.toDomain() } }
    fun observeWatchHistory(limit: Int = 40): Flow<List<WatchHistoryItem>> = activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeWatchHistory(id, limit)
    }.map { list -> list.map { it.toDomain() } }

    fun observeCompletedEpisodeIds(seriesId: Int): Flow<Set<String>> = activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(emptySet())
        else dao.observeCompletedEpisodeIds(id, seriesId).map { it.toSet() }
    }

    suspend fun warmStreamUrl(url: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1")
            .get()
            .build()
        runCatching {
            okHttpClient.newBuilder()
                .callTimeout(4, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { }
        }
    }

    suspend fun diagnoseConnection(): ConnectionDiagnostics {
        val credentials = savedCredentials.first()
            ?: error("Aucun serveur IPTV configuré")

        val authResponse = try {
            api.authenticate(
                playerApi(credentials.serverUrl),
                credentials.username,
                credentials.password
            )
        } catch (e: HttpException) {
            val apiProbe = probeFromHttpCode("API Xtream", e.code())
            val skipped = ConnectionProbe("Direct", false, null, "Non testé : API refusée")
            return ConnectionDiagnostics(apiProbe, skipped, skipped.copy(label = "VOD"))
        } catch (e: Exception) {
            val apiProbe = ConnectionProbe("API Xtream", false, null, e.message ?: "Erreur réseau")
            val skipped = ConnectionProbe("Direct", false, null, "Non testé : API inaccessible")
            return ConnectionDiagnostics(apiProbe, skipped, skipped.copy(label = "VOD"))
        }

        val authenticated = authResponse.userInfo?.auth == 1
        val apiProbe = if (authenticated) {
            ConnectionProbe("API Xtream", true, 200, "OK")
        } else {
            ConnectionProbe("API Xtream", false, 200, authResponse.userInfo?.message ?: "Compte refusé")
        }
        if (!authenticated) {
            val skipped = ConnectionProbe("Direct", false, null, "Non testé : compte refusé")
            return ConnectionDiagnostics(apiProbe, skipped, skipped.copy(label = "VOD"))
        }

        val liveProbe = runCatching {
            api.getLiveStreams(
                playerApi(credentials.serverUrl),
                credentials.username,
                credentials.password
            ).firstOrNull { dto -> !dto.name.trim().matches(Regex("^#{3,}.*#{3,}$")) }
        }.fold(
            onSuccess = { stream ->
                if (stream == null) ConnectionProbe("Direct", false, null, "Aucun flux live retourné")
                else probeStream(
                    "Direct",
                    "${normalizeServer(credentials.serverUrl)}/live/${credentials.username}/${credentials.password}/${stream.streamId}.${credentials.outputFormat}"
                )
            },
            onFailure = { error -> probeFromThrowable("Direct", error) }
        )

        val vodProbe = runCatching {
            api.getVodStreams(
                playerApi(credentials.serverUrl),
                credentials.username,
                credentials.password
            ).firstOrNull()
        }.fold(
            onSuccess = { stream ->
                if (stream == null) ConnectionProbe("VOD", false, null, "Aucun film retourné par le serveur")
                else {
                    val ext = stream.containerExtension?.ifBlank { null } ?: "mp4"
                    probeStream(
                        "VOD",
                        "${normalizeServer(credentials.serverUrl)}/movie/${credentials.username}/${credentials.password}/${stream.streamId}.$ext"
                    )
                }
            },
            onFailure = { error -> probeFromThrowable("VOD", error) }
        )

        return ConnectionDiagnostics(apiProbe, liveProbe, vodProbe)
    }

    private fun probeStream(label: String, url: String): ConnectionProbe {
        val client = okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    ConnectionProbe(label, true, response.code, "OK")
                } else {
                    probeFromHttpCode(label, response.code)
                }
            }
        } catch (e: Exception) {
            ConnectionProbe(label, false, null, e.message ?: "Erreur réseau")
        }
    }

    private fun probeFromThrowable(label: String, error: Throwable): ConnectionProbe =
        if (error is HttpException) {
            probeFromHttpCode(label, error.code())
        } else {
            ConnectionProbe(label, false, null, error.message ?: "Erreur réseau")
        }

    private fun probeFromHttpCode(label: String, code: Int): ConnectionProbe {
        val message = when (code) {
            401 -> "Identifiants refusés"
            403 -> "Accès refusé depuis cette connexion / cette IP"
            404 -> "Ressource introuvable"
            429 -> "Trop de connexions ou limitation serveur"
            else -> "HTTP $code"
        }
        return ConnectionProbe(label, false, code, message)
    }

    private suspend fun refreshXmltvFallbackIfNeeded(
        credentials: XtreamCredentials,
        profileId: String
    ) = xmltvMutex.withLock {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastXmltvRefreshAtMs < TimeUnit.HOURS.toMillis(6)) return@withLock
        if (nowMs - lastXmltvAttemptAtMs < TimeUnit.MINUTES.toMillis(5)) return@withLock
        lastXmltvAttemptAtMs = nowMs

        val channels = observeAllChannels().first()
            .filter { !it.epgChannelId.isNullOrBlank() }
        if (channels.isEmpty()) return@withLock

        val streamByEpgId = channels
            .groupBy { it.epgChannelId!!.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, values) -> values.map { it.streamId }.distinct() }

        val xmltvUrl = normalizeXtreamServer(credentials.serverUrl)
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .build()

        val publicImported = importXmltvUrl(
            url = "https://iptv-epg.org/files/epg-fr.xml",
            profileId = profileId,
            streamByEpgId = streamByEpgId,
            sourceLabel = "fr-public"
        )
        val imported = if (publicImported > 0) {
            publicImported
        } else {
            importXmltvUrl(
                url = xmltvUrl.toString(),
                profileId = profileId,
                streamByEpgId = streamByEpgId,
                sourceLabel = "provider"
            )
        }

        if (imported > 0) {
            lastXmltvRefreshAtMs = System.currentTimeMillis()
            dao.deleteExpiredEpg(profileId, System.currentTimeMillis() / 1000L - 86_400L)
            Log.d("TVFlowEpg", "XMLTV imported=$imported channels=${streamByEpgId.size}")
        } else {
            Log.w("TVFlowEpg", "XMLTV sources returned no current matching programmes")
        }
    }

    private suspend fun importXmltvUrl(
        url: String,
        profileId: String,
        streamByEpgId: Map<String, List<Int>>,
        sourceLabel: String
    ): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("TVFlowEpg", "XMLTV[$sourceLabel] HTTP ${response.code}")
                return@use 0
            }
            val body = response.body ?: return@use 0
            val length = body.contentLength()
            Log.d("TVFlowEpg", "XMLTV[$sourceLabel] response length=$length type=${body.contentType()}")
            if (length in 0..1023) {
                Log.w("TVFlowEpg", "XMLTV[$sourceLabel] response too small: $length bytes")
                return@use 0
            }
            parseXmltvIntoDatabase(body.byteStream(), profileId, streamByEpgId)
                .also { Log.d("TVFlowEpg", "XMLTV[$sourceLabel] imported=$it") }
        }
    }

    private suspend fun parseXmltvIntoDatabase(
        input: InputStream,
        profileId: String,
        streamByEpgId: Map<String, List<Int>>
    ): Int {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, null)
        }
        val now = System.currentTimeMillis() / 1000L
        val windowStart = now - TimeUnit.HOURS.toSeconds(2)
        val windowEnd = now + TimeUnit.HOURS.toSeconds(36)
        val clearedStreams = HashSet<Int>()
        val batch = ArrayList<EpgProgramEntity>(400)
        var imported = 0

        var targetStreams: List<Int>? = null
        var start = 0L
        var stop = 0L
        var title: String? = null
        var description: String? = null

        suspend fun flush() {
            if (batch.isNotEmpty()) {
                dao.upsertEpg(batch.toList())
                batch.clear()
            }
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        val xmlChannelId = parser.getAttributeValue(null, "channel")
                            ?.trim()
                            ?.lowercase(Locale.ROOT)
                        targetStreams = xmlChannelId?.let(streamByEpgId::get)
                        start = parseXmltvTimestamp(parser.getAttributeValue(null, "start")) ?: 0L
                        stop = parseXmltvTimestamp(parser.getAttributeValue(null, "stop")) ?: 0L
                        title = null
                        description = null
                    }
                    "title" -> if (targetStreams != null && title == null) {
                        title = parser.nextText().trim()
                    }
                    "desc" -> if (targetStreams != null && description == null) {
                        description = parser.nextText().trim().takeIf { it.isNotBlank() }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    val streams = targetStreams
                    if (!streams.isNullOrEmpty() && start > 0L && stop > start && stop >= windowStart && start <= windowEnd) {
                        for (streamId in streams) {
                            if (clearedStreams.add(streamId)) dao.clearEpg(profileId, streamId)
                            batch += EpgProgramEntity(
                                profileId = profileId,
                                streamId = streamId,
                                startTimestamp = start,
                                stopTimestamp = stop,
                                title = title?.takeIf { it.isNotBlank() } ?: "Programme sans titre",
                                description = description,
                                hasArchive = false
                            )
                            imported++
                            if (batch.size >= 400) flush()
                        }
                    }
                    targetStreams = null
                }
            }
            event = parser.next()
        }
        flush()
        Log.d("TVFlowEpg", "XMLTV parse imported=$imported window=$windowStart..$windowEnd")
        return imported
    }

    suspend fun prefetchEpg(streamIds: List<Int>, limit: Int = 8, parallelism: Int = 4) = coroutineScope {
        val semaphore = Semaphore(parallelism.coerceAtLeast(1))
        streamIds.distinct().take(24).map { streamId -> async { semaphore.withPermit { runCatching { refreshEpg(streamId, limit) } } } }.forEach { it.await() }
    }

    suspend fun logout() { credentialsStore.clear() }

    suspend fun prewarmLivePresentation() {
        val firstCategory = observeCategories().first { it.isNotEmpty() }.first()
        observeChannels(firstCategory.id).first { it.isNotEmpty() }
    }

    private suspend fun currentProfileId(): String = activeProfileId.first() ?: error("Aucun profil IPTV actif")
    private fun applyStreamVisibility(
        source: Flow<List<LiveChannelEntity>>,
        cacheScope: String? = null,
        frenchOnly: Boolean = false
    ): Flow<List<LiveChannel>> {
        if (cacheScope == null) {
            return combine(
                source.distinctUntilChanged().onEach { Log.d("TVFlowFilter", "raw=${it.size}") },
                settingsStore.playerPreferences.distinctUntilChanged().onEach { Log.d("TVFlowFilter", "prefs ready") }
            ) { list, prefs ->
                prepareVisibleChannels(list, prefs.hideOfflineStreams, frenchOnly)
            }.flowOn(Dispatchers.Default)
        }

        return settingsStore.playerPreferences.distinctUntilChanged().flatMapLatest { prefs ->
            val resultKey = "$cacheScope|offline=${prefs.hideOfflineStreams}"
            flow {
                synchronized(liveResultCache) { liveResultCache[resultKey] }
                    ?.let { cached ->
                        Log.d("TVFlowFilter", "memory-hit=${cached.size} first=${cached.firstOrNull()?.name}")
                        emit(cached)
                    }
                emitAll(
                    source.distinctUntilChanged().map { list ->
                        Log.d("TVFlowFilter", "raw=${list.size}")
                        prepareVisibleChannels(list, prefs.hideOfflineStreams, frenchOnly).also { prepared ->
                            synchronized(liveResultCache) { liveResultCache[resultKey] = prepared }
                        }
                    }
                )
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    private fun prepareVisibleChannels(
        list: List<LiveChannelEntity>,
        hideOfflineStreams: Boolean,
        frenchOnly: Boolean
    ): List<LiveChannel> {
        val mapped = mapChannels(list)
        val prepared = (if (frenchOnly) prepareFrenchChannelsCached(mapped) else mapped)
            .filterNot { hideOfflineStreams && it.health == StreamHealth.OFFLINE }
        Log.d(
            "TVFlowFilter",
            "prepared=${prepared.size} frenchOnly=$frenchOnly first=${prepared.firstOrNull()?.name}"
        )
        return prepared
    }

    private fun mapChannels(list: List<LiveChannelEntity>) = list
        .map { it.toDomain() }
        .filterNot { it.isSectionMarker }
    private fun LiveChannelEntity.toDomain() = LiveChannel(
        streamId, name, categoryId, logoUrl, epgChannelId, streamType,
        isFavorite, lastWatchedAt, isHidden, customOrder,
        runCatching { StreamHealth.valueOf(healthStatus) }.getOrDefault(StreamHealth.UNKNOWN),
        healthCheckedAt, healthFailureCount, lastHttpCode, lastPlaybackError,
        logicalNumber = null, tvArchive = tvArchive, tvArchiveDuration = tvArchiveDuration
    )
    private fun VodItemEntity.toDomain() = VodItem(
        streamId = streamId,
        name = name,
        categoryId = categoryId,
        posterUrl = posterUrl,
        rating = rating,
        added = added,
        containerExtension = containerExtension,
        synopsis = synopsis,
        releaseYear = releaseYear,
        releaseMonth = releaseMonth
    )
    private fun SeriesItemEntity.toDomain() = SeriesItem(seriesId, name, categoryId, coverUrl, rating, plot, releaseYear, releaseMonth)
    private fun PlaybackPositionEntity.toDomain() = PlaybackPosition(
        mediaKey, positionMs, durationMs, updatedAt, title, imageUrl, kind, mediaId, containerExtension, seriesId
    )
    private fun WatchHistoryEntity.toDomain() = WatchHistoryItem(
        mediaKey = mediaKey,
        kind = kind,
        mediaId = mediaId,
        seriesId = seriesId,
        title = title,
        imageUrl = imageUrl,
        containerExtension = containerExtension,
        positionMs = positionMs,
        durationMs = durationMs,
        watchedAt = watchedAt,
        completed = completed
    )

    private fun decodeEpgText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            val decoded = Base64.decode(value, Base64.DEFAULT)
            val text = decoded.toString(Charsets.UTF_8)
            if (text.isNotBlank() && text.all { !it.isISOControl() || it == '\n' || it == '\t' }) text else value
        }.getOrDefault(value)
    }

    private fun playerApi(server: String): String = buildPlayerApiUrl(server)
    private fun normalizeServer(raw: String): String = normalizeXtreamServer(raw)
}

internal fun classifyStreamHealth(httpCode: Int?, error: String?): StreamHealth = when (httpCode) {
    403, 429 -> StreamHealth.UNSTABLE
    404, 410 -> StreamHealth.OFFLINE
    else -> if (
        error?.contains("DECOD", ignoreCase = true) == true ||
        error?.contains("PARS", ignoreCase = true) == true
    ) StreamHealth.UNPLAYABLE else StreamHealth.UNSTABLE
}

internal fun parseReleaseYearMonth(releaseDate: String?, yearValue: String?): Pair<Int?, Int?> {
    val yearFromField = yearValue
        ?.let { Regex("""\b(?:19|20)\d{2}\b""").find(it)?.value }
        ?.toIntOrNull()

    if (!releaseDate.isNullOrBlank()) {
        val yearFirst = Regex("""((?:19|20)\d{2})[-/.](\d{1,2})""").find(releaseDate)
        if (yearFirst != null) {
            val year = yearFirst.groupValues[1].toIntOrNull()
            val month = yearFirst.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 }
            return year to month
        }
        val dayFirst = Regex("""\d{1,2}[-/.](\d{1,2})[-/.]((?:19|20)\d{2})""").find(releaseDate)
        if (dayFirst != null) {
            val month = dayFirst.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 }
            val year = dayFirst.groupValues[2].toIntOrNull()
            return year to month
        }
        val yearOnly = Regex("""\b(?:19|20)\d{2}\b""").find(releaseDate)?.value?.toIntOrNull()
        if (yearOnly != null) return yearOnly to null
    }
    return yearFromField to null
}

internal fun normalizeXtreamServer(raw: String): String {
    var value = raw.trim().trimEnd('/')
    value = value.replace(
        Regex("(?i)/(player_api\\.php|get\\.php)(?:\\?.*)?$"),
        ""
    ).trimEnd('/')
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "http://$value"
    }
    return value
}

internal fun buildPlayerApiUrl(server: String): String =
    "${normalizeXtreamServer(server)}/player_api.php"

internal fun buildLiveStreamUrl(credentials: XtreamCredentials, streamId: Int): String {
    val output = credentials.outputFormat.lowercase()
        .takeIf { it in setOf("ts", "m3u8") } ?: "ts"
    return "${normalizeXtreamServer(credentials.serverUrl)}/live/${credentials.username}/${credentials.password}/$streamId.$output"
}

internal fun buildCatchupStreamUrl(
    credentials: XtreamCredentials,
    streamId: Int,
    startTimestamp: Long,
    stopTimestamp: Long
): String {
    val durationMinutes = ((stopTimestamp - startTimestamp).coerceAtLeast(60L) + 59L) / 60L
    val start = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        .format(Date(startTimestamp * 1000L))
    return "${normalizeXtreamServer(credentials.serverUrl)}/timeshift/${credentials.username}/${credentials.password}/$durationMinutes/$start/$streamId.ts"
}

internal fun buildVodStreamUrl(
    credentials: XtreamCredentials,
    streamId: Int,
    containerExtension: String?
): String {
    val ext = containerExtension?.ifBlank { null } ?: "mp4"
    return "${normalizeXtreamServer(credentials.serverUrl)}/movie/${credentials.username}/${credentials.password}/$streamId.$ext"
}

internal fun buildEpisodeStreamUrl(
    credentials: XtreamCredentials,
    episodeId: String,
    containerExtension: String?
): String {
    val ext = containerExtension?.ifBlank { null } ?: "mp4"
    return "${normalizeXtreamServer(credentials.serverUrl)}/series/${credentials.username}/${credentials.password}/$episodeId.$ext"
}

private data class ParsedLiveCategory(
    val entity: LiveCategoryEntity,
    val provider: String?,
    val displayName: String,
    val root: String,
    val suffix: String
)

internal fun buildPrimaryLiveCategories(
    list: List<LiveCategoryEntity>,
    overrides: Map<String, String> = emptyMap()
): List<LiveCategory> {
    val visible = list.filterNot { it.name.trim().matches(Regex("^#{3,}.*#{3,}$")) }
    fun section(entity: LiveCategoryEntity): MediaSection {
        val overridden = overrides["LIVE:" + entity.id]?.let { value ->
            runCatching { MediaSection.valueOf(value) }.getOrNull()
        }
        return overridden ?: when {
            isKidsProviderCategory(entity) -> MediaSection.KIDS
            isFranceProviderCategory(entity) -> MediaSection.FRANCE
            else -> MediaSection.OTHER
        }
    }
    val kids = visible.filter { section(it) == MediaSection.KIDS }
    val kidsIds = kids.asSequence().map { it.id }.toSet()
    val france = visible.filter { it.id !in kidsIds && section(it) == MediaSection.FRANCE }
    val franceIds = france.asSequence().map { it.id }.toSet()
    val others = visible.filter { it.id !in kidsIds && it.id !in franceIds }

    return buildList {
        buildPrimaryCategory(
            name = "FRANCE",
            prefix = FRANCE_CATEGORY_PREFIX,
            source = france,
            subFilterLabel = ::franceSubFilterLabel
        )?.let(::add)
        buildPrimaryCategory(
            name = "ENFANTS",
            prefix = KIDS_CATEGORY_PREFIX,
            source = kids,
            subFilterLabel = ::providerSubFilterLabel
        )?.let(::add)
        buildPrimaryCategory(
            name = "AUTRES",
            prefix = OTHER_CATEGORY_PREFIX,
            source = others,
            subFilterLabel = ::providerSubFilterLabel
        )?.let(::add)
    }
}

private fun buildPrimaryCategory(
    name: String,
    prefix: String,
    source: List<LiveCategoryEntity>,
    subFilterLabel: (LiveCategoryEntity) -> String
): LiveCategory? {
    if (source.isEmpty()) return null
    val ordered = source.sortedBy { it.sortOrder }
    return LiveCategory(
        id = prefix + ordered.joinToString(",") { it.id },
        name = name,
        order = ordered.minOf { it.sortOrder },
        isLocked = ordered.any { it.isLocked },
        subFilters = ordered.map { category ->
            LiveSubFilter(
                id = prefix + category.id,
                name = subFilterLabel(category)
            )
        }
    )
}

private fun isFranceProviderCategory(entity: LiveCategoryEntity): Boolean {
    val parsed = parseProviderCategory(entity)
    val combined = buildString {
        parsed.provider?.let {
            append(it)
            append(' ')
        }
        append(parsed.displayName)
    }
    return frenchCategoryScore(combined) >= FRENCH_CATEGORY_THRESHOLD
}

private fun isKidsProviderCategory(entity: LiveCategoryEntity): Boolean {
    val normalized = normalizeCategoryName(parseProviderCategory(entity).displayName)
    return KIDS_CATEGORY_MARKERS.any { marker -> normalized.contains(marker) }
}

private fun franceSubFilterLabel(entity: LiveCategoryEntity): String {
    val display = parseProviderCategory(entity).displayName
    return display.removePrefix("FRANCE").trim().ifBlank { "Principal" }
}

private fun providerSubFilterLabel(entity: LiveCategoryEntity): String {
    val parsed = parseProviderCategory(entity)
    return parsed.provider?.let { provider -> provider + " • " + parsed.displayName }
        ?: parsed.displayName
}

private fun normalizeCategoryName(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(CATEGORY_DIACRITICS, "")
        .uppercase(Locale.ROOT)

private fun normalizeSearchText(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(CATEGORY_DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private const val FRENCH_CATEGORY_THRESHOLD = 4

private fun categoryTokens(name: String): List<String> =
    normalizeCategoryName(name)
        .replace(Regex("[^A-Z0-9]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

internal fun frenchCategoryScore(name: String): Int {
    val normalized = normalizeCategoryName(name)
    val tokens = categoryTokens(name)
    if (tokens.isEmpty() && !name.contains("🇫🇷")) return 0

    var score = if (name.contains("🇫🇷")) 6 else 0

    // Strong, explicit French markers. These should work regardless of provider separators.
    tokens.forEach { token ->
        when {
            token in STRONG_FRENCH_CATEGORY_MARKERS -> score += 5
            token in FRENCH_CATEGORY_CODES -> score += 4
            token == "VOST" && "FR" in tokens -> score += 3
        }
    }

    // Bracketed / suffixed provider language tags are usually highly intentional.
    if (Regex("(?i)\\[(FR|FRA|FRE|VF|VOSTFR)]").containsMatchIn(name)) score += 3
    if (Regex("(?i)(^|[|:_ -])(FR|FRA|FRE)(?=$|[|:_ -])").containsMatchIn(name)) score += 2

    // "MULTI FR", "AUDIO FR", "DUB FR" are common provider conventions.
    if (tokens.any { it in FRENCH_AUDIO_HINTS } && tokens.any { it in FRENCH_CATEGORY_CODES || it in STRONG_FRENCH_CATEGORY_MARKERS }) {
        score += 2
    }

    // An explicit non-French language should make weak/ambiguous French hints less likely
    // to steal a category. Strong French markers still win for mixed-language categories.
    val foreignCount = tokens.count { it in FOREIGN_LANGUAGE_CODES }
    score -= foreignCount * 3

    // Avoid accidental matches inside geography/provider names such as AFRICA.
    if (normalized.contains("AFRICA") || normalized.contains("AFRIQUE")) {
        score -= 1
    }

    return score
}

internal fun classifyMediaSection(name: String): MediaSection {
    val normalized = normalizeCategoryName(name)
    if (KIDS_CATEGORY_MARKERS.any { marker -> normalized.contains(marker) }) {
        return MediaSection.KIDS
    }
    return if (frenchCategoryScore(name) >= FRENCH_CATEGORY_THRESHOLD) {
        MediaSection.FRANCE
    } else {
        MediaSection.OTHER
    }
}

internal fun groupProviderCategories(list: List<LiveCategoryEntity>): List<LiveCategory> {
    val parsed = list
        .filterNot { it.name.trim().matches(Regex("^#{3,}.*#{3,}$")) }
        .map(::parseProviderCategory)
    val grouped = parsed.groupBy { item ->
        item.provider?.let { provider -> provider.uppercase() + "|" + item.root.uppercase() }
            ?: "single:" + item.entity.id
    }

    return buildList {
        val emitted = mutableSetOf<String>()
        parsed.forEach { item ->
            val key = item.provider?.let { provider -> provider.uppercase() + "|" + item.root.uppercase() }
                ?: "single:" + item.entity.id
            if (!emitted.add(key)) return@forEach
            val members = grouped[key].orEmpty()
            if (members.size <= 1) {
                add(item.entity.toDisplayCategory(item.displayName))
            } else {
                add(groupedCategory(item, members))
            }
        }
    }
}

private fun groupedCategory(
    first: ParsedLiveCategory,
    members: List<ParsedLiveCategory>
): LiveCategory = LiveCategory(
    id = GROUP_CATEGORY_PREFIX + members.joinToString(",") { it.entity.id },
    name = first.root,
    order = members.minOf { it.entity.sortOrder },
    isLocked = members.any { it.entity.isLocked },
    subFilters = members.map { member ->
        LiveSubFilter(
            id = member.entity.id,
            name = member.suffix.ifBlank { "Principal" }
        )
    }
)

private fun parseProviderCategory(entity: LiveCategoryEntity): ParsedLiveCategory {
    val raw = entity.name.trim()
    val match = PROVIDER_CATEGORY_REGEX.matchEntire(raw)
    val provider = match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    val display = match?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() } ?: raw
    val root = display.substringBefore(' ').trim().ifBlank { display }
    val suffix = display.removePrefix(root).trim()
    return ParsedLiveCategory(entity, provider, display, root, suffix)
}

private fun LiveCategoryEntity.toDisplayCategory(displayName: String) =
    LiveCategory(id, displayName, sortOrder, isHidden, isLocked)

internal fun decodeCategoryGroupId(categoryId: String): List<String> {
    val prefix = when {
        categoryId.startsWith(FRANCE_CATEGORY_PREFIX) -> FRANCE_CATEGORY_PREFIX
        categoryId.startsWith(KIDS_CATEGORY_PREFIX) -> KIDS_CATEGORY_PREFIX
        categoryId.startsWith(OTHER_CATEGORY_PREFIX) -> OTHER_CATEGORY_PREFIX
        categoryId.startsWith(GROUP_CATEGORY_PREFIX) -> GROUP_CATEGORY_PREFIX
        else -> null
    }
    return if (prefix != null) {
        categoryId.removePrefix(prefix)
            .split(',')
            .filter { it.isNotBlank() }
    } else {
        listOf(categoryId)
    }
}


internal fun parseEpgTimestamp(value: String?): Long? {
    val raw = value?.trim()?.toLongOrNull() ?: return null
    if (raw <= 0L) return null
    return if (raw >= 100_000_000_000L) raw / 1_000L else raw
}

internal fun parseXmltvTimestamp(value: String?): Long? {
    val raw = value?.trim().orEmpty()
    if (raw.length < 14) return null
    val datePart = raw.take(14)
    val zonePart = raw.drop(14).trim().takeIf { it.matches(Regex("[+-]\\d{4}")) }
    val pattern = if (zonePart == null) "yyyyMMddHHmmss" else "yyyyMMddHHmmss Z"
    val input = if (zonePart == null) datePart else "$datePart $zonePart"
    return runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            .parse(input)
            ?.time
            ?.div(1000L)
    }.getOrNull()
}
