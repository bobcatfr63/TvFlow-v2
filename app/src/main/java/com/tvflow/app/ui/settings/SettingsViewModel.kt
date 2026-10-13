package com.tvflow.app.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.local.PlayerPreferences
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.data.metadata.TmdbMetadataClient
import com.tvflow.app.data.metadata.TmdbEnrichmentTracker
import com.tvflow.app.data.metadata.TmdbEnrichmentStats
import com.tvflow.app.data.refresh.CatalogRefreshWorker
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.ConnectionDiagnostics
import com.tvflow.app.domain.LiveCategory
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.MediaCategory
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.ServerProfile
import com.tvflow.app.launcher.TvLauncherMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RefreshProgress(
    val percent: Int,
    val step: String
)

data class SettingsUiState(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
    val player: PlayerPreferences = PlayerPreferences(),
    val pinConfigured: Boolean = false,
    val tmdbConfigured: Boolean = false,
    val tmdbStats: TmdbEnrichmentStats = TmdbEnrichmentStats(),
    val categories: List<LiveCategory> = emptyList(),
    val vodCategories: List<MediaCategory> = emptyList(),
    val seriesCategories: List<MediaCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCategoryIds: Set<String> = emptySet(),
    val selectedChannelIds: Set<Int> = emptySet(),
    val channels: List<LiveChannel> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val diagnostics: ConnectionDiagnostics? = null
)

private data class AccountSettings(
    val profiles: List<ServerProfile>,
    val activeProfileId: String?,
    val player: PlayerPreferences,
    val pinConfigured: Boolean,
    val tmdbConfigured: Boolean
)

private data class CatalogSettings(
    val categories: List<LiveCategory>,
    val vodCategories: List<MediaCategory>,
    val seriesCategories: List<MediaCategory>,
    val selectedCategoryId: String?,
    val selectedCategoryIds: Set<String>,
    val selectedChannelIds: Set<Int>,
    val channels: List<LiveChannel>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val settingsStore: SettingsStore,
    private val tmdbMetadataClient: TmdbMetadataClient,
    private val tmdbEnrichmentTracker: TmdbEnrichmentTracker,
    private val application: Application
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val selectedCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    private val selectedChannelIds = MutableStateFlow<Set<Int>>(emptySet())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val diagnostics = MutableStateFlow<ConnectionDiagnostics?>(null)
    private val tmdbValid = MutableStateFlow<Boolean?>(null)
    private val _refreshProgress = MutableStateFlow<RefreshProgress?>(null)
    val refreshProgress: StateFlow<RefreshProgress?> = _refreshProgress

    private val channels = selectedCategoryId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeManageChannels(id)
    }

    private val validatedTmdbConfigured = combine(settingsStore.tmdbToken, tmdbValid) { token, valid ->
        !token.isNullOrBlank() && valid != false
    }

    private val account = combine(
        repository.profiles,
        repository.activeProfileId,
        settingsStore.playerPreferences,
        settingsStore.parentalPinConfigured,
        validatedTmdbConfigured
    ) { profiles, activeId, player, pinConfigured, tmdbConfigured ->
        AccountSettings(profiles, activeId, player, pinConfigured, tmdbConfigured)
    }

    private data class LiveCatalogSettings(
        val categories: List<LiveCategory>,
        val selectedCategoryId: String?,
        val selectedCategoryIds: Set<String>,
        val selectedChannelIds: Set<Int>,
        val channels: List<LiveChannel>
    )

    private val liveCatalog = combine(
        repository.observeManageCategories(),
        selectedCategoryId,
        selectedCategoryIds,
        selectedChannelIds,
        channels
    ) { categories, selectedId, bulkSelectedIds, bulkSelectedChannelIds, channelList ->
        LiveCatalogSettings(
            categories = categories,
            selectedCategoryId = selectedId,
            selectedCategoryIds = bulkSelectedIds,
            selectedChannelIds = bulkSelectedChannelIds,
            channels = channelList
        )
    }

    private val mediaCategories = combine(
        repository.observeMediaCategories(MediaType.VOD, includeHidden = true),
        repository.observeMediaCategories(MediaType.SERIES, includeHidden = true)
    ) { vod, series -> vod to series }

    private val catalog = combine(liveCatalog, mediaCategories) { live, media ->
        CatalogSettings(
            categories = live.categories,
            vodCategories = media.first,
            seriesCategories = media.second,
            selectedCategoryId = live.selectedCategoryId,
            selectedCategoryIds = live.selectedCategoryIds,
            selectedChannelIds = live.selectedChannelIds,
            channels = live.channels
        )
    }

    init {
        viewModelScope.launch {
            val current = settingsStore.currentTmdbToken()
            if (current.isNullOrBlank()) {
                tmdbValid.value = null
            } else {
                val status = tmdbMetadataClient.validateCredential(current)
                tmdbValid.value = status in 200..299
                if (status == 401 || status == 403) {
                    message.value = "Le token TMDb enregistré est refusé (HTTP $status). Remplace-le dans Métadonnées films & séries."
                }
            }
        }
    }

    private val tmdbStats = combine(repository.activeProfileId, tmdbEnrichmentTracker.revision) { id, _ ->
        tmdbEnrichmentTracker.snapshot(id)
    }

    val state: StateFlow<SettingsUiState> = combine(
        account,
        catalog,
        busy,
        message,
        diagnostics
    ) { accountState, catalogState, isBusy, info, networkDiagnostics ->
        SettingsUiState(
            profiles = accountState.profiles,
            activeProfileId = accountState.activeProfileId,
            player = accountState.player,
            pinConfigured = accountState.pinConfigured,
            tmdbConfigured = accountState.tmdbConfigured,
            categories = catalogState.categories,
            vodCategories = catalogState.vodCategories,
            seriesCategories = catalogState.seriesCategories,
            selectedCategoryId = catalogState.selectedCategoryId,
            selectedCategoryIds = catalogState.selectedCategoryIds,
            selectedChannelIds = catalogState.selectedChannelIds,
            channels = catalogState.channels,
            busy = isBusy,
            message = info,
            diagnostics = networkDiagnostics
        )
    }.combine(tmdbStats) { settings, statistics ->
        settings.copy(tmdbStats = statistics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectCategory(id: String) {
        if (selectedCategoryId.value == id) return
        selectedCategoryId.value = id
        selectedChannelIds.value = emptySet()
    }

    fun toggleCategorySelection(id: String) {
        selectedCategoryIds.value = selectedCategoryIds.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
    }

    fun selectAllCategories() {
        selectedCategoryIds.value = state.value.categories.map { it.id }.toSet()
    }

    fun clearCategorySelection() {
        selectedCategoryIds.value = emptySet()
    }

    fun toggleChannelSelection(streamId: Int) {
        selectedChannelIds.value = selectedChannelIds.value.toMutableSet().apply {
            if (!add(streamId)) remove(streamId)
        }
    }

    fun selectAllChannels() {
        selectedChannelIds.value = state.value.channels.map { it.streamId }.toSet()
    }

    fun clearChannelSelection() {
        selectedChannelIds.value = emptySet()
    }

    fun setSelectedChannelsHidden(hidden: Boolean) {
        val ids = selectedChannelIds.value
        if (ids.isEmpty()) {
            message.value = "Sélectionne au moins une chaîne"
            return
        }
        launchBusy(if (hidden) "Chaînes sélectionnées masquées" else "Chaînes sélectionnées affichées") {
            ids.forEach { streamId -> repository.setChannelHidden(streamId, hidden) }
        }
    }

    fun setSelectedCategoriesHidden(hidden: Boolean) {
        val ids = selectedCategoryIds.value
        if (ids.isEmpty()) {
            message.value = "Sélectionne au moins une catégorie"
            return
        }
        launchBusy(if (hidden) "Catégories sélectionnées masquées" else "Catégories sélectionnées affichées") {
            ids.forEach { id -> repository.setCategoryHidden(id, hidden) }
        }
    }

    fun switchProfile(id: String) = launchBusy("Profil activé") { repository.switchProfile(id); selectedCategoryId.value = null }
    fun deleteProfile(id: String) = launchBusy("Profil supprimé") { repository.deleteProfile(id); selectedCategoryId.value = null }
    fun refresh() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            _refreshProgress.value = RefreshProgress(0, "Préparation")
            val result = runCatching {
                repository.refreshAllWithProgress { percent, step ->
                    _refreshProgress.value = RefreshProgress(percent.coerceIn(0, 100), step)
                }
            }
            if (result.isSuccess) {
                _refreshProgress.value = RefreshProgress(100, "Actualisation terminée")
                delay(500)
                message.value = "Catalogues actualisés"
            } else {
                message.value = result.exceptionOrNull()?.message ?: "Échec de l’actualisation"
            }
            _refreshProgress.value = null
            busy.value = false
        }
    }

    fun enrichTmdbNow() {
        if (busy.value) return
        viewModelScope.launch {
            if (!state.value.tmdbConfigured) {
                message.value = "Configure et valide d'abord ta clé TMDB."
                return@launch
            }
            busy.value = true
            val categories = mediaCategories.first()
            // A bounded pass avoids blocking the UI on exceptionally large IPTV catalogs.
            val tasks = (categories.first.map { MediaType.VOD to it.id } +
                categories.second.map { MediaType.SERIES to it.id })
                .distinct().take(120)
            val startedProfile = repository.activeProfileId.first()
            var done = 0
            var errors = 0
            try {
                for ((type, categoryId) in tasks) {
                    if (repository.activeProfileId.first() != startedProfile) break
                    _refreshProgress.value = RefreshProgress(
                        if (tasks.isEmpty()) 100 else done * 100 / tasks.size,
                        "Enrichissement TMDB : catégorie ${done + 1}/${tasks.size}"
                    )
                    val result = runCatching {
                        repository.enrichMissingMediaMetadata(type, categoryId, limit = 12)
                    }
                    if (result.isFailure) errors++
                    done++
                }
                message.value = "TMDB : $done catégories parcourues, $errors erreur(s). Consulte les compteurs d'enrichissement ci-dessous."
            } finally {
                _refreshProgress.value = null
                busy.value = false
            }
        }
    }

    fun runConnectionDiagnostics() {
        viewModelScope.launch {
            busy.value = true
            diagnostics.value = null
            val result = runCatching { repository.diagnoseConnection() }
            diagnostics.value = result.getOrNull()
            message.value = result.exceptionOrNull()?.message
            busy.value = false
        }
    }

    fun setBuffer(value: String) { viewModelScope.launch { settingsStore.setBufferPreset(value) } }
    fun setScale(value: String) { viewModelScope.launch { settingsStore.setVideoScale(value) } }
    fun setSubtitles(value: Boolean) { viewModelScope.launch { settingsStore.setSubtitlesEnabled(value) } }
    fun setAccent(value: String) { viewModelScope.launch { settingsStore.setAccent(value) } }
    fun setAutoNextEpisode(value: Boolean) { viewModelScope.launch { settingsStore.setAutoNextEpisode(value) } }
    fun setNextEpisodeCountdownSeconds(value: Int) { viewModelScope.launch { settingsStore.setNextEpisodeCountdownSeconds(value) } }
    fun setHideOfflineStreams(value: Boolean) { viewModelScope.launch { settingsStore.setHideOfflineStreams(value) } }
    fun setAutoStartOnTvBoot(value: Boolean) { viewModelScope.launch { settingsStore.setAutoStartOnTvBoot(value) } }
    fun setResumeLastLiveOnTvBoot(value: Boolean) { viewModelScope.launch { settingsStore.setResumeLastLiveOnTvBoot(value) } }
    fun setTmdbToken(value: String) {
        viewModelScope.launch {
            if (value.isBlank()) {
                settingsStore.setTmdbToken("")
                tmdbValid.value = null
                message.value = "TMDb désactivé"
                return@launch
            }
            busy.value = true
            val status = tmdbMetadataClient.validateCredential(value)
            if (status in 200..299) {
                settingsStore.setTmdbToken(value)
                tmdbValid.value = true
                message.value = "TMDb validé et enregistré localement"
            } else {
                tmdbValid.value = false
                message.value = if (status != null) {
                    "TMDb refuse ce token (HTTP $status). Génère un nouveau token/API Key puis réessaie."
                } else {
                    "Impossible de valider TMDb pour le moment. Vérifie la connexion et réessaie."
                }
            }
            busy.value = false
        }
    }

    fun setTvLauncherEnabled(value: Boolean) {
        viewModelScope.launch {
            val result = TvLauncherMode.setEnabled(application, value)
            if (result.isSuccess) {
                settingsStore.setTvLauncherEnabled(value)
                val status = TvLauncherMode.getStatus(application)
                message.value = if (value && status.tvFlowAvailable) {
                    "TVFlow est disponible comme écran d’accueil sur cet appareil"
                } else if (value) {
                    "Le système n’expose pas TVFlow comme écran d’accueil"
                } else {
                    "Mode écran d’accueil TVFlow désactivé"
                }
            } else {
                message.value = result.exceptionOrNull()?.message
                    ?: "Cet appareil ne permet pas d’activer TVFlow comme écran d’accueil"
            }
        }
    }

    fun openHomeChooser() {
        val result = TvLauncherMode.openHomeChooser(application)
        message.value = result.getOrElse {
            it.message ?: "Impossible d’ouvrir le sélecteur d’écran d’accueil sur cet appareil"
        }
    }

    fun openAndroidSettings() {
        val result = TvLauncherMode.openAndroidSettings(application)
        message.value = result.getOrElse {
            it.message ?: "Impossible d’ouvrir les réglages Android sur cet appareil"
        }
    }
    fun setRefreshIntervalDays(days: Int) {
        viewModelScope.launch {
            settingsStore.setRefreshIntervalDays(days)
            CatalogRefreshWorker.schedule(application, days)
            message.value = if (days <= 0) "Actualisation automatique désactivée" else "Actualisation automatique : tous les $days jour${if (days > 1) "s" else ""}"
        }
    }

    fun setMobileHomePreviewMode(value: String) {
        viewModelScope.launch {
            settingsStore.setMobileHomePreviewMode(value)
            message.value = when (value) {
                "OFF" -> "Aperçus animés désactivés sur téléphone"
                "ALWAYS" -> "Aperçus animés autorisés sur Wi‑Fi et données mobiles"
                else -> "Aperçus animés limités au Wi‑Fi"
            }
        }
    }

    fun setPin(pin: String) = launchBusy("PIN parental enregistré") { settingsStore.setParentalPin(pin) }
    fun clearPin() = launchBusy("PIN parental supprimé") {
        repository.observeManageCategories().first().filter { it.isLocked }.forEach { repository.setCategoryLocked(it.id, false) }
        settingsStore.clearParentalPin()
    }

    fun toggleCategoryHidden(category: LiveCategory) = launchBusy(null) { repository.setCategoryHidden(category.id, !category.isHidden) }
    fun toggleCategoryLocked(category: LiveCategory) {
        if (!category.isLocked && !state.value.pinConfigured) {
            message.value = "Configure d’abord un PIN parental"
            return
        }
        launchBusy(null) { repository.setCategoryLocked(category.id, !category.isLocked) }
    }
    fun moveCategory(category: LiveCategory, delta: Int) = launchBusy(null) { repository.moveCategory(category.id, delta) }
    fun classifyCategory(category: LiveCategory, section: com.tvflow.app.domain.MediaSection) =
        launchBusy("Classement mémorisé pour ce serveur") {
            repository.setCategorySectionOverride(category.id, section)
        }
    fun resetCategoryClassification(category: LiveCategory) =
        launchBusy("Classement automatique réactivé") {
            repository.setCategorySectionOverride(category.id, null)
        }

    fun classifyMediaCategory(
        category: MediaCategory,
        section: MediaSection
    ) = launchBusy("Classement mémorisé pour ce serveur") {
        repository.setMediaCategorySectionOverride(category.type, category.id, section)
    }

    fun resetMediaCategoryClassification(category: MediaCategory) =
        launchBusy("Classement automatique réactivé") {
            repository.setMediaCategorySectionOverride(category.type, category.id, null)
        }

    val hiddenMediaKeys = repository.observeHiddenMediaCategoryKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setMediaCategoriesHidden(categories: List<MediaCategory>, hidden: Boolean) {
        if (categories.isEmpty()) return
        launchBusy(if (hidden) "Catégories masquées" else "Catégories affichées") {
            categories.forEach { repository.setMediaCategoryHidden(it.type, it.id, hidden) }
        }
    }

    fun classifyMediaCategories(categories: List<MediaCategory>, section: MediaSection?) {
        if (categories.isEmpty()) return
        launchBusy("${categories.size} catégories mises à jour") {
            categories.forEach { category ->
                repository.setMediaCategorySectionOverride(category.type, category.id, section)
            }
        }
    }

    fun toggleChannelHidden(channel: LiveChannel) = launchBusy(null) { repository.setChannelHidden(channel.streamId, !channel.isHidden) }
    fun moveChannel(channel: LiveChannel, delta: Int) {
        val category = selectedCategoryId.value ?: return
        launchBusy(null) { repository.moveChannel(category, channel.streamId, delta) }
    }

    private fun launchBusy(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            val result = runCatching { block() }
            message.value = result.exceptionOrNull()?.message ?: successMessage
            busy.value = false
        }
    }
}
