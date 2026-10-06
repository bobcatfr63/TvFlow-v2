package com.tvflow.app.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.Episode
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val offerNextEpisode: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    private val repository: XtreamRepository,
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

    init { prepare() }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val current = _state.value
        val key = current.mediaKey.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            repository.savePlaybackPosition(key, positionMs, durationMs, current.title, current.imageUrl, kind, mediaId, ext)
        }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        if (kind != "episode" || _state.value.nextEpisode == null || durationMs <= 0L) return
        val remainingMs = durationMs - positionMs
        val shouldOffer = !nextEpisodeDismissed && remainingMs <= 90_000L
        if (_state.value.offerNextEpisode != shouldOffer) {
            _state.value = _state.value.copy(offerNextEpisode = shouldOffer)
        }
    }

    fun onPlaybackEnded() {
        if (kind == "episode" && _state.value.nextEpisode != null) {
            nextEpisodeDismissed = false
            _state.value = _state.value.copy(offerNextEpisode = true)
        }
    }

    fun dismissNextEpisode() {
        nextEpisodeDismissed = true
        _state.value = _state.value.copy(offerNextEpisode = false)
    }

    fun onPlaybackError(errorCodeName: String) {
        _state.value = _state.value.copy(error = "Lecture Media3 impossible ($errorCodeName)")
    }

    private fun prepare() {
        viewModelScope.launch {
            runCatching {
                val key: String
                val url: String
                if (kind == "episode") {
                    val episode = Episode(mediaId, 0, 0, title, ext, null, null)
                    key = "episode:$mediaId"
                    url = repository.episodeUrl(episode)
                } else {
                    val id = mediaId.toInt()
                    val item = VodItem(id, title, "", null, null, null, ext)
                    key = "vod:$id"
                    url = repository.vodUrl(item)
                }
                val savedResume = repository.playbackPosition(key)?.positionMs ?: 0L
                val resume = handoffPositionMs.takeIf { it > 0L } ?: savedResume
                val navigation = if (kind == "episode" && seriesId != null) {
                    val episodes = repository.getSeriesEpisodes(seriesId)
                    val index = episodes.indexOfFirst { it.id == mediaId }
                    EpisodeNavigation(
                        previous = if (index > 0) episodes.getOrNull(index - 1) else null,
                        next = if (index >= 0) episodes.getOrNull(index + 1) else null
                    )
                } else {
                    EpisodeNavigation()
                }
                PlaybackPreparation(key, url, resume, navigation.previous, navigation.next)
            }.onSuccess { prepared ->
                _state.value = _state.value.copy(
                    streamUrl = prepared.url,
                    mediaKey = prepared.key,
                    resumePositionMs = prepared.resumeMs,
                    previousEpisode = prepared.previousEpisode,
                    nextEpisode = prepared.nextEpisode,
                    error = null
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(error = error.message ?: "Lecture impossible")
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
        val previousEpisode: Episode?,
        val nextEpisode: Episode?
    )
}
