package com.tvflow.app.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.tvflow.app.BuildConfig
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.AppUpdateChecker
import com.tvflow.app.data.AppUpdateInfo
import com.tvflow.app.data.AppUpdateManager
import com.tvflow.app.data.InstalledReleaseNotes
import com.tvflow.app.data.UpdateDownloadState
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.PlaybackPosition
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TonightProgram(
    val channel: LiveChannel,
    val program: EpgProgram
)

data class DashboardUiState(
    val serverName: String? = null,
    val continueWatching: List<PlaybackPosition> = emptyList(),
    val recentlyWatched: List<PlaybackPosition> = emptyList(),
    val favorites: List<LiveChannel> = emptyList(),
    val recentChannels: List<LiveChannel> = emptyList(),
    val tonightPrograms: List<TonightProgram> = emptyList(),
    val recentVodReleases: List<VodItem> = emptyList(),
    val recentSeriesReleases: List<SeriesItem> = emptyList(),
    val recommendedVod: List<VodItem> = emptyList(),
    val recommendedSeries: List<SeriesItem> = emptyList(),
    val backgroundVideoUrl: String? = null,
    val backgroundVideoTitle: String? = null,
    val mobileHomePreviewMode: String = "WIFI",
    val updateInfo: AppUpdateInfo? = null,
    val updateDownloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    val installedReleaseNotesVersion: String? = null,
    val installedReleaseNotes: List<String> = emptyList()
)

private data class DashboardBackdrop(val url: String, val title: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val updateChecker: AppUpdateChecker,
    private val updateManager: AppUpdateManager,
    private val settingsStore: SettingsStore,
    private val smartCache: com.tvflow.app.data.local.SmartCache
) : ViewModel() {
    suspend fun vignettePreviewUrl(vod: VodItem?, series: SeriesItem?): String? = runCatching {
        when {
            vod != null -> repository.vodUrl(vod)
            series != null -> repository.getSeriesEpisodes(series.seriesId).firstOrNull()?.let { repository.episodeUrl(it) }
            else -> null
        }
    }.getOrNull()

    private val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    private var updateDismissed = false
    private val updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    private val backgroundPreview = MutableStateFlow<DashboardBackdrop?>(null)
    private val installedReleaseNotes = MutableStateFlow<InstalledReleaseNotesState?>(null)

    private val favorites = repository.observeFavorites()
    private val recents = repository.observeRecentChannels(16)
    private val allChannels = repository.observeAllChannels()

    private val eveningCandidates = combine(favorites, recents, allChannels) { fav, recent, all ->
        (fav + recent + all.take(40))
            .distinctBy { it.streamId }
            .filterNot { it.isSectionMarker }
            .take(40)
    }.distinctUntilChanged()

    private val tonightPrograms = eveningCandidates.flatMapLatest { channels ->
        if (channels.isEmpty()) {
            flowOf(emptyList())
        } else {
            repository.observeEpgForStreams(channels.map { it.streamId }).map { epg ->
                buildTonightPrograms(channels, epg)
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { repository.prewarmLivePresentation() }
                .onFailure { Log.w("TVFlowHome", "Live prewarm failed", it) }
        }
        viewModelScope.launch {
            val lastShown = settingsStore.lastReleaseNotesVersion.first()
            val currentVersion = InstalledReleaseNotes.versionName
            val currentBuildKey = "$currentVersion (${BuildConfig.VERSION_CODE})"
            if (lastShown != currentBuildKey && InstalledReleaseNotes.items.isNotEmpty()) {
                installedReleaseNotes.value = InstalledReleaseNotesState(
                    versionName = currentVersion,
                    items = InstalledReleaseNotes.items
                )
            }
        }
        // Update availability is checked once by the global navigation overlay.
        viewModelScope.launch {
            var currentStreamId: Int? = null
            while (true) {
                val preview = runCatching { repository.randomVodPreview(currentStreamId) }
                    .onFailure { Log.w("TVFlowHome", "Background preview selection failed", it) }
                    .getOrNull()
                if (preview != null) {
                    val (item, url) = preview
                    currentStreamId = item.streamId
                    backgroundPreview.value = DashboardBackdrop(url = url, title = item.name)
                }
                delay(60_000L)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            eveningCandidates.collectLatest { channels ->
                runCatching {
                    repository.prefetchEpg(
                        streamIds = channels.take(18).map { it.streamId },
                        limit = 12,
                        parallelism = 4
                    )
                }.onFailure {
                    Log.d("TVFlowHome", "Préchargement EPG accueil indisponible: " + it.message)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.observeMediaCategories(MediaType.VOD)
                    .first { it.isNotEmpty() }
                    .take(2)
                    .forEach { category ->
                        repository.enrichMissingMediaMetadata(MediaType.VOD, category.id, limit = 6)
                    }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.observeMediaCategories(MediaType.SERIES)
                    .first { it.isNotEmpty() }
                    .take(2)
                    .forEach { category ->
                        repository.enrichMissingMediaMetadata(MediaType.SERIES, category.id, limit = 6)
                    }
            }
        }
    }

    private data class InstalledReleaseNotesState(
        val versionName: String,
        val items: List<String>
    )

    private data class DashboardSupplemental(
        val updateInfo: AppUpdateInfo?,
        val downloadState: UpdateDownloadState,
        val backdrop: DashboardBackdrop?,
        val recentVod: List<VodItem>,
        val recentSeries: List<SeriesItem>
    )

    // Recommendations follow the visible categories selected in Settings.
    // No Pulse onboarding, explicit taste profile, or legacy France-only filter.
    private val recommendedVod = repository.activeProfileId.flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList()) else
            repository.observeMediaCategories(MediaType.VOD).flatMapLatest { categories ->
                val visible = categories.map { it.id }
                flow {
                    val saved = smartCache.loadVod(profile, visible)
                    if (saved.isNotEmpty()) emit(saved)
                    emitAll(repository.observeRecommendedVod().onEach {
                        smartCache.saveVod(profile, visible, it)
                    })
                }
            }
    }
    private val recommendedSeries = repository.activeProfileId.flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList()) else
            repository.observeMediaCategories(MediaType.SERIES).flatMapLatest { categories ->
                val visible = categories.map { it.id }
                flow {
                    val saved = smartCache.loadSeries(profile, visible)
                    if (saved.isNotEmpty()) emit(saved)
                    emitAll(repository.observeRecommendedSeries().onEach {
                        smartCache.saveSeries(profile, visible, it)
                    })
                }
            }
    }

    private val supplementalState = combine(
        updateInfo,
        updateDownloadState,
        backgroundPreview,
        repository.observeRecentFrenchVodReleases(12),
        repository.observeRecentFrenchSeriesReleases(12)
    ) { info, download, backdrop, recentVod, recentSeries ->
        DashboardSupplemental(
            updateInfo = info,
            downloadState = download,
            backdrop = backdrop,
            recentVod = recentVod,
            recentSeries = recentSeries
        )
    }

    private data class DashboardContent(
        val watching: List<PlaybackPosition>,
        val history: List<PlaybackPosition>,
        val favorites: List<LiveChannel>,
        val recents: List<LiveChannel>,
        val serverName: String?,
        val tonight: List<TonightProgram>
    )

    private val history = repository.observeWatchHistory(16).map { list ->
        list.map { item ->
            PlaybackPosition(
                mediaKey = item.mediaKey,
                positionMs = if (item.completed) item.durationMs else item.positionMs,
                durationMs = item.durationMs,
                updatedAt = item.watchedAt,
                title = item.title,
                imageUrl = item.imageUrl,
                kind = item.kind,
                mediaId = item.mediaId,
                containerExtension = item.containerExtension,
                seriesId = item.seriesId
            )
        }
    }

    private val coreContent = combine(
        repository.observeContinueWatching(12),
        history,
        favorites,
        recents,
        repository.activeProfile
    ) { watching, historyItems, fav, recent, profile ->
        DashboardContent(
            watching = watching,
            history = historyItems,
            favorites = fav,
            recents = recent,
            serverName = profile?.name,
            tonight = emptyList()
        )
    }

    private val contentState = combine(coreContent, tonightPrograms) { content, tonight ->
        content.copy(tonight = tonight)
    }

    private val recommendations = combine(
        recommendedVod.onStart { emit(emptyList()) },
        recommendedSeries.onStart { emit(emptyList()) }
    ) { vod, series -> vod to series }

    val state: StateFlow<DashboardUiState> = combine(
        contentState.onStart {
            emit(DashboardContent(emptyList(), emptyList(), emptyList(), emptyList(), null, emptyList()))
        },
        supplementalState.onStart {
            emit(DashboardSupplemental(null, UpdateDownloadState.Idle, null, emptyList(), emptyList()))
        },
        installedReleaseNotes,
        recommendations,
        settingsStore.playerPreferences
    ) { content, supplemental, installedNotes, suggested, playerPreferences ->
        DashboardUiState(
            serverName = content.serverName,
            continueWatching = content.watching,
            recentlyWatched = content.history.take(12),
            favorites = content.favorites.take(12),
            recentChannels = content.recents.take(12),
            tonightPrograms = content.tonight.take(16),
            recentVodReleases = supplemental.recentVod,
            recentSeriesReleases = supplemental.recentSeries,
            recommendedVod = suggested.first,
            recommendedSeries = suggested.second,
            backgroundVideoUrl = supplemental.backdrop?.url,
            backgroundVideoTitle = supplemental.backdrop?.title,
            mobileHomePreviewMode = playerPreferences.mobileHomePreviewMode,
            updateInfo = supplemental.updateInfo,
            updateDownloadState = supplemental.downloadState,
            installedReleaseNotesVersion = installedNotes?.versionName,
            installedReleaseNotes = installedNotes?.items.orEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun checkUpdates() {
        if (BuildConfig.IS_PLAY_STORE || updateDismissed || updateInfo.value != null) return
        viewModelScope.launch { updateChecker.check()?.let { updateInfo.value = it } }
    }

    fun downloadUpdate() {
        if (BuildConfig.IS_PLAY_STORE) return
        val info = updateInfo.value ?: return
        if (updateDownloadState.value is UpdateDownloadState.Downloading) return
        viewModelScope.launch {
            updateDownloadState.value = UpdateDownloadState.Downloading(0)
            runCatching {
                updateManager.download(info) { percent ->
                    updateDownloadState.value = UpdateDownloadState.Downloading(percent)
                }
            }.onSuccess { apk ->
                updateDownloadState.value = UpdateDownloadState.Ready(apk.absolutePath)
            }.onFailure { error ->
                updateDownloadState.value = UpdateDownloadState.Error(error.message ?: "Échec de la mise à jour.")
            }
        }
    }

    fun installUpdate(context: Context) {
        val ready = updateDownloadState.value as? UpdateDownloadState.Ready ?: return
        runCatching {
            val intent = if (updateManager.canInstallPackages()) {
                updateManager.installIntent(ready.apkPath)
            } else {
                updateManager.unknownSourcesIntent() ?: updateManager.installIntent(ready.apkPath)
            }
            context.startActivity(intent)
        }.onFailure { error ->
            updateDownloadState.value = UpdateDownloadState.Error(error.message ?: "Impossible d’ouvrir l’installation.")
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
        updateInfo.value = null
        updateDownloadState.value = UpdateDownloadState.Idle
    }

    fun dismissInstalledReleaseNotes() {
        val notes = installedReleaseNotes.value ?: return
        installedReleaseNotes.value = null
        viewModelScope.launch {
            settingsStore.markReleaseNotesShown("${notes.versionName} (${BuildConfig.VERSION_CODE})")
        }
    }


}

private fun normalizedTasteText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

private fun buildTonightPrograms(
    channels: List<LiveChannel>,
    epg: List<EpgProgram>
): List<TonightProgram> {
    val channelById = channels.associateBy { it.streamId }
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 20)
    calendar.set(Calendar.MINUTE, 30)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val primeStart = calendar.timeInMillis / 1000L
    calendar.set(Calendar.HOUR_OF_DAY, 22)
    calendar.set(Calendar.MINUTE, 0)
    val primeEnd = calendar.timeInMillis / 1000L

    // Xtream EPG often has no genre field, so classify conservatively.
    fun isFilm(item: TonightProgram): Boolean {
        val details = normalizedTasteText(
            "${item.program.title} ${item.program.description.orEmpty()}"
        )
        val excluded = Regex("\\b(serie|episode|journal|actualites|magazine|sport|match|documentaire)\\b")
        if (excluded.containsMatchIn(details)) return false
        val channel = normalizedTasteText(item.channel.name)
        return Regex("\\b(film|cinema|long metrage)\\b").containsMatchIn(details) ||
            Regex("\\b(cine|cinema|movies)\\b").containsMatchIn(channel)
    }

    return epg.asSequence()
        .filter { it.startTimestamp <= primeEnd && it.stopTimestamp > primeStart && it.startTimestamp >= primeStart - 45 * 60 }
        .mapNotNull { program ->
            channelById[program.streamId]?.let { channel -> TonightProgram(channel, program) }
        }
        .filter(::isFilm)
        .distinctBy { it.channel.streamId.toString() + ":" + it.program.startTimestamp }
        .sortedWith(
            compareBy<TonightProgram> { it.program.startTimestamp }
                .thenByDescending { it.channel.isFavorite }
        )
        .take(24)
        .toList()
}
