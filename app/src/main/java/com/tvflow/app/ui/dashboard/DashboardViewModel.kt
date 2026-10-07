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
import com.tvflow.app.data.local.TastePreferences
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.MediaSection
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val pulseVod: List<VodItem> = emptyList(),
    val pulseSeries: List<SeriesItem> = emptyList(),
    val tasteThemes: Set<String> = emptySet(),
    val learnedThemes: List<String> = emptyList(),
    val showTasteOnboarding: Boolean = false,
    val backgroundVideoUrl: String? = null,
    val backgroundVideoTitle: String? = null,
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
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
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
        viewModelScope.launch { runCatching { repository.prewarmLivePresentation() } }
        viewModelScope.launch {
            val lastShown = settingsStore.lastReleaseNotesVersion.first()
            val currentVersion = InstalledReleaseNotes.versionName
            if (lastShown != currentVersion && InstalledReleaseNotes.items.isNotEmpty()) {
                installedReleaseNotes.value = InstalledReleaseNotesState(
                    versionName = currentVersion,
                    items = InstalledReleaseNotes.items
                )
            }
        }
        viewModelScope.launch { updateInfo.value = updateChecker.check() }
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
                    .filter { it.section == MediaSection.FRANCE }
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
                    .filter { it.section == MediaSection.FRANCE }
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

    private data class TasteState(
        val preferences: TastePreferences,
        val learnedThemes: List<String>,
        val pulseVod: List<VodItem>,
        val pulseSeries: List<SeriesItem>
    )

    private val recentVodForTaste = repository.observeRecentFrenchVodReleases(30)
    private val recentSeriesForTaste = repository.observeRecentFrenchSeriesReleases(30)
    private val learnedThemes = repository.observeWatchHistory(40).map {
        runCatching { repository.learnedMediaCategoryNames(40) }
            .getOrDefault(emptyList())
            .flatMap(::detectTasteThemes)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(5)
    }

    private val tasteState = combine(
        settingsStore.tastePreferences,
        learnedThemes,
        recentVodForTaste,
        recentSeriesForTaste,
        repository.observeMediaCategories(MediaType.VOD)
    ) { preferences, learned, vodItems, seriesItems, vodCategories ->
        val categoryNames = vodCategories.associate { it.id to it.name }
        val seriesCategoryNames = repository.observeMediaCategories(MediaType.SERIES).first()
            .associate { it.id to it.name }
        val activeThemes = (preferences.themes + learned).toSet()

        val pulseVod = vodItems
            .sortedByDescending { item ->
                recommendationScore(
                    text = listOfNotNull(categoryNames[item.categoryId], item.name, item.synopsis).joinToString(" "),
                    explicitThemes = preferences.themes,
                    learnedThemes = learned
                )
            }
            .let { scored ->
                val matched = scored.filter {
                    recommendationScore(
                        listOfNotNull(categoryNames[it.categoryId], it.name, it.synopsis).joinToString(" "),
                        preferences.themes,
                        learned
                    ) > 0
                }
                (if (matched.isNotEmpty()) matched else scored).take(12)
            }

        val pulseSeries = seriesItems
            .sortedByDescending { item ->
                recommendationScore(
                    text = listOfNotNull(seriesCategoryNames[item.categoryId], item.name, item.plot).joinToString(" "),
                    explicitThemes = preferences.themes,
                    learnedThemes = learned
                )
            }
            .let { scored ->
                val matched = scored.filter {
                    recommendationScore(
                        listOfNotNull(seriesCategoryNames[it.categoryId], it.name, it.plot).joinToString(" "),
                        preferences.themes,
                        learned
                    ) > 0
                }
                (if (matched.isNotEmpty()) matched else scored).take(12)
            }

        TasteState(
            preferences = preferences,
            learnedThemes = learned,
            pulseVod = if (activeThemes.isEmpty()) emptyList() else pulseVod,
            pulseSeries = if (activeThemes.isEmpty()) emptyList() else pulseSeries
        )
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

    val state: StateFlow<DashboardUiState> = combine(
        contentState,
        supplementalState,
        installedReleaseNotes,
        tasteState
    ) { content, supplemental, installedNotes, taste ->
        DashboardUiState(
            serverName = content.serverName,
            continueWatching = content.watching,
            recentlyWatched = content.history.take(12),
            favorites = content.favorites.take(12),
            recentChannels = content.recents.take(12),
            tonightPrograms = content.tonight.take(16),
            recentVodReleases = supplemental.recentVod,
            recentSeriesReleases = supplemental.recentSeries,
            pulseVod = taste.pulseVod,
            pulseSeries = taste.pulseSeries,
            tasteThemes = taste.preferences.themes,
            learnedThemes = taste.learnedThemes,
            showTasteOnboarding = !taste.preferences.onboardingCompleted,
            backgroundVideoUrl = supplemental.backdrop?.url,
            backgroundVideoTitle = supplemental.backdrop?.title,
            updateInfo = supplemental.updateInfo,
            updateDownloadState = supplemental.downloadState,
            installedReleaseNotesVersion = installedNotes?.versionName,
            installedReleaseNotes = installedNotes?.items.orEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun downloadUpdate() {
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
        updateInfo.value = null
        updateDownloadState.value = UpdateDownloadState.Idle
    }

    fun dismissInstalledReleaseNotes() {
        val notes = installedReleaseNotes.value ?: return
        installedReleaseNotes.value = null
        viewModelScope.launch {
            settingsStore.markReleaseNotesShown(notes.versionName)
        }
    }

    fun saveTasteProfile(themes: Set<String>) {
        viewModelScope.launch {
            settingsStore.saveTastePreferences(themes, completed = true)
        }
    }

    fun skipTasteProfile() {
        viewModelScope.launch {
            settingsStore.saveTastePreferences(emptySet(), completed = true)
        }
    }
}

private val TASTE_THEME_TOKENS = mapOf(
    "Action" to listOf("action"),
    "Aventure" to listOf("aventure", "adventure"),
    "Comédie" to listOf("comedie", "comedy", "humour"),
    "Thriller" to listOf("thriller", "suspense"),
    "Science-fiction" to listOf("science fiction", "science-fiction", "sci fi", "sci-fi", "sf"),
    "Fantastique" to listOf("fantastique", "fantasy"),
    "Romance" to listOf("romance", "romantique"),
    "Policier" to listOf("policier", "crime", "detective"),
    "Horreur" to listOf("horreur", "horror"),
    "Documentaire" to listOf("documentaire", "documentary"),
    "Animation" to listOf("animation", "anime", "manga"),
    "Famille" to listOf("famille", "family", "kids", "enfant")
)

private fun normalizedTasteText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

private fun detectTasteThemes(value: String): List<String> {
    val normalized = normalizedTasteText(value)
    return TASTE_THEME_TOKENS.mapNotNull { (theme, tokens) ->
        theme.takeIf { tokens.any(normalized::contains) }
    }
}

private fun recommendationScore(
    text: String,
    explicitThemes: Set<String>,
    learnedThemes: List<String>
): Int {
    val detected = detectTasteThemes(text).toSet()
    if (detected.isEmpty()) return 0
    val explicitScore = detected.count { it in explicitThemes } * 5
    val learnedScore = detected.sumOf { theme ->
        val index = learnedThemes.indexOf(theme)
        if (index < 0) 0 else (learnedThemes.size - index).coerceAtLeast(1)
    }
    return explicitScore + learnedScore
}

private fun buildTonightPrograms(
    channels: List<LiveChannel>,
    epg: List<EpgProgram>
): List<TonightProgram> {
    val channelById = channels.associateBy { it.streamId }
    val now = System.currentTimeMillis() / 1000L
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 18)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val eveningStart = calendar.timeInMillis / 1000L
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    val eveningEnd = calendar.timeInMillis / 1000L
    val windowStart = if (now > eveningStart) now else eveningStart

    return epg.asSequence()
        .filter { it.stopTimestamp > windowStart && it.startTimestamp <= eveningEnd }
        .mapNotNull { program ->
            channelById[program.streamId]?.let { channel -> TonightProgram(channel, program) }
        }
        .distinctBy { it.channel.streamId.toString() + ":" + it.program.startTimestamp }
        .sortedWith(
            compareByDescending<TonightProgram> { it.channel.isFavorite }
                .thenByDescending { it.channel.lastWatchedAt ?: 0L }
                .thenBy { if (it.program.isLiveAt(now)) 0 else 1 }
                .thenBy { it.program.startTimestamp }
        )
        .toList()
}
