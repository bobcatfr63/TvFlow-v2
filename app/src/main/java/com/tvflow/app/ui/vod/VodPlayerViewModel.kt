package com.tvflow.app.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.domain.Episode
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VodPlayerUiState(
    val streamUrl: String? = null,
    val mediaKey: String = "",
    val resumePositionMs: Long = 0L,
    val title: String = "",
    val imageUrl: String? = null,
    val containerExtension: String? = null,
    val seriesId: Int? = null,
    val previousEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
    val nextEpisodeUrl: String? = null,
    val offerNextEpisode: Boolean = false,
    val autoNextEpisode: Boolean = true,
    val nextEpisodeCountdownSeconds: Int = 10,
    val autoNextCountdown: Int? = null,
    val error: String? = null
)

@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val settingsStore: SettingsStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val kind: String = savedStateHandle["kind"] ?: "vod"
    private val mediaId: String = checkNotNull(savedStateHandle["mediaId"])
    private val title: String = savedStateHandle["title"] ?: "Lecture"
    private val ext: String? = savedStateHandle.get<String>("ext")?.takeUnless { it == "_" }
    private val seriesId: Int? = savedStateHandle.get<Int>("seriesId")?.takeIf { it >= 0 }
    private val handoffPositionMs: Long = savedStateHandle.get<Long>("handoffPositionMs")?.coerceAtLeast(0L) ?: 0L

    private val _state = MutableStateFlow(VodPlayerUiState(title = title, containerExtension = ext, seriesId = seriesId))
    private var nextEpisodeDismissed = false
    val state: StateFlow<VodPlayerUiState> = _state.asStateFlow()

    init {
        prepare()
        viewModelScope.launch {
            settingsStore.playerPreferences.collect { prefs ->
                _state.value = _state.value.copy(
                    autoNextEpisode = prefs.autoNextEpisode,
                    nextEpisodeCountdownSeconds = prefs.nextEpisodeCountdownSeconds,
                    autoNextCountdown = if (prefs.autoNextEpisode) _state.value.autoNextCountdown else null
                )
            }
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val current = _state.value
        val key = current.mediaKey.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            repository.savePlaybackPosition(
                mediaKey = key,
                positionMs = positionMs,
                durationMs = durationMs,
                title = current.title,
                imageUrl = current.imageUrl,
                kind = kind,
                mediaId = mediaId,
                containerExtension = ext,
                seriesId = seriesId
            )
        }
    }

    fun saveFinalProgress(positionMs: Long, durationMs: Long) {
        val current = _state.value
        val key = current.mediaKey.takeIf { it.isNotBlank() } ?: return
        // Finish the last Room write even if navigation clears this ViewModel.
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            repository.savePlaybackPosition(
                mediaKey = key,
                positionMs = positionMs,
                durationMs = durationMs,
                title = current.title,
                imageUrl = current.imageUrl,
                kind = kind,
                mediaId = mediaId,
                containerExtension = ext,
                seriesId = seriesId
            )
        }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        val current = _state.value
        if (kind != "episode" || current.nextEpisode == null || durationMs <= 0L) return
        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        val shouldOffer = !nextEpisodeDismissed && remainingMs <= 90_000L
        val autoCountdown = if (
            current.autoNextEpisode &&
            !nextEpisodeDismissed &&
            remainingMs <= current.nextEpisodeCountdownSeconds * 1_000L
        ) {
            ((remainingMs + 999L) / 1_000L).toInt().coerceAtLeast(0)
        } else {
            null
        }
        if (current.offerNextEpisode != shouldOffer || current.autoNextCountdown != autoCountdown) {
            _state.value = current.copy(
                offerNextEpisode = shouldOffer,
                autoNextCountdown = autoCountdown
            )
        }
    }

    fun onPlaybackEnded(durationMs: Long = 0L, positionMs: Long = durationMs) {
        val current = _state.value
        if (com.tvflow.app.BuildConfig.APPLICATION_ID.endsWith(".next") &&
            !isCompletePlayback(positionMs, durationMs)) {
            saveFinalProgress(positionMs, durationMs)
            _state.value = current.copy(
                offerNextEpisode = false,
                autoNextCountdown = null,
                error = "Le flux s’est interrompu avant la fin. Votre progression a été conservée."
            )
            return
        }
        val key = current.mediaKey
        if (key.isNotBlank()) {
            viewModelScope.launch {
                repository.markPlaybackCompleted(
                    mediaKey = key,
                    title = current.title,
                    imageUrl = current.imageUrl,
                    kind = kind,
                    mediaId = mediaId,
                    containerExtension = ext,
                    seriesId = seriesId,
                    durationMs = durationMs
                )
            }
        }
        if (kind == "episode" && current.nextEpisode != null) {
            nextEpisodeDismissed = false
            _state.value = current.copy(
                offerNextEpisode = true,
                autoNextCountdown = if (current.autoNextEpisode) 0 else null
            )
        }
    }

    fun dismissNextEpisode() {
        nextEpisodeDismissed = true
        _state.value = _state.value.copy(
            offerNextEpisode = false,
            autoNextCountdown = null
        )
    }

    fun onPlaybackError(errorCodeName: String) {
        _state.value = _state.value.copy(error = "Lecture Media3 impossible ($errorCodeName)")
    }

    private fun prepare() {
        viewModelScope.launch {
            runCatching {
                val key: String
                val url: String
                var currentEpisode: Episode? = null
                val navigation: EpisodeNavigation

                if (kind == "episode") {
                    val episodes = if (seriesId != null) repository.getSeriesEpisodes(seriesId) else emptyList()
                    val index = episodes.indexOfFirst { it.id == mediaId }
                    currentEpisode = episodes.getOrNull(index)
                        ?: Episode(mediaId, 0, 0, title, ext, null, null)
                    key = "episode:$mediaId"
                    url = repository.episodeUrl(currentEpisode)
                    navigation = EpisodeNavigation(
                        previous = if (index > 0) episodes.getOrNull(index - 1) else null,
                        next = if (index >= 0) episodes.getOrNull(index + 1) else null
                    )
                } else {
                    val id = mediaId.toInt()
                    val item = VodItem(id, title, "", null, null, null, ext)
                    key = "vod:$id"
                    url = repository.vodUrl(item)
                    navigation = EpisodeNavigation()
                }

                val savedResume = repository.playbackPosition(key)?.positionMs ?: 0L
                val resume = handoffPositionMs.takeIf { it > 0L } ?: savedResume
                val nextUrl = navigation.next?.let { repository.episodeUrl(it) }

                if (!nextUrl.isNullOrBlank()) {
                    launch { repository.warmStreamUrl(nextUrl) }
                }

                PlaybackPreparation(
                    key = key,
                    url = url,
                    resumeMs = resume,
                    imageUrl = currentEpisode?.imageUrl,
                    previousEpisode = navigation.previous,
                    nextEpisode = navigation.next,
                    nextEpisodeUrl = nextUrl
                )
            }.onSuccess { prepared ->
                _state.value = _state.value.copy(
                    streamUrl = prepared.url,
                    mediaKey = prepared.key,
                    resumePositionMs = prepared.resumeMs,
                    imageUrl = prepared.imageUrl,
                    previousEpisode = prepared.previousEpisode,
                    nextEpisode = prepared.nextEpisode,
                    nextEpisodeUrl = prepared.nextEpisodeUrl,
                    error = null
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = _state.value.copy(error = "Lecture indisponible. Vérifiez la connexion au serveur IPTV puis réessayez.")
            }
        }
    }

    private data class EpisodeNavigation(
        val previous: Episode? = null,
        val next: Episode? = null
    )

    private data class PlaybackPreparation(
        val key: String,
        val url: String,
        val resumeMs: Long,
        val imageUrl: String?,
        val previousEpisode: Episode?,
        val nextEpisode: Episode?,
        val nextEpisodeUrl: String?
    )
}
