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
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.PlaybackPosition
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val favorites: List<LiveChannel> = emptyList(),
    val recentChannels: List<LiveChannel> = emptyList(),
    val tonightPrograms: List<TonightProgram> = emptyList(),
    val recentVodReleases: List<VodItem> = emptyList(),
    val recentSeriesReleases: List<SeriesItem> = emptyList(),
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
            val preview = runCatching { repository.randomVodPreview() }
                .onFailure { Log.w("TVFlowHome", "Background preview selection failed", it) }
                .getOrNull()
            backgroundPreview.value = preview?.let { (item, url) ->
                DashboardBackdrop(url = url, title = item.name)
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
        val favorites: List<LiveChannel>,
        val recents: List<LiveChannel>,
        val serverName: String?,
        val tonight: List<TonightProgram>
    )

    private val contentState = combine(
        repository.observeContinueWatching(12),
        favorites,
        recents,
        repository.activeProfile,
        tonightPrograms
    ) { watching, fav, recent, profile, tonight ->
        DashboardContent(
            watching = watching,
            favorites = fav,
            recents = recent,
            serverName = profile?.name,
            tonight = tonight
        )
    }

    val state: StateFlow<DashboardUiState> = combine(
        contentState,
        supplementalState,
        installedReleaseNotes
    ) { content, supplemental, installedNotes ->
        DashboardUiState(
            serverName = content.serverName,
            continueWatching = content.watching,
            favorites = content.favorites.take(12),
            recentChannels = content.recents.take(12),
            tonightPrograms = content.tonight.take(16),
            recentVodReleases = supplemental.recentVod,
            recentSeriesReleases = supplemental.recentSeries,
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
