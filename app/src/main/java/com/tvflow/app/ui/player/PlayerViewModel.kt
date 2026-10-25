package com.tvflow.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.data.repository.selectTntChannels
import com.tvflow.app.ui.guide.GUIDE_ALL
import com.tvflow.app.ui.guide.GUIDE_FAVORITES
import com.tvflow.app.ui.guide.GUIDE_TNT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import javax.inject.Inject

data class PlayerUiState(
    val channel: LiveChannel? = null,
    val streamUrl: String? = null,
    val restartUrl: String? = null,
    val channelIndex: Int = -1,
    val channelCount: Int = 0,
    val channels: List<LiveChannel> = emptyList(),
    val nowProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val epg: List<EpgProgram> = emptyList(),
    val epgLoading: Boolean = false,
    val playerError: String? = null,
    val previousStreamId: Int? = null,
    val bufferPreset: String = "FAST",
    val videoScale: String = "FIT",
    val subtitlesEnabled: Boolean = true
)

private data class PlayerStatus(
    val epgLoading: Boolean,
    val error: String?,
    val previousStreamId: Int?,
    val restartUrl: String?,
    val bufferPreset: String,
    val videoScale: String,
    val subtitlesEnabled: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val settingsStore: SettingsStore,
    playbackQueue: LivePlaybackQueue,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val initialStreamId: Int = checkNotNull(savedStateHandle["streamId"])
    private val categoryId: String = savedStateHandle["categoryId"] ?: ""
    private val selectedStreamId = MutableStateFlow(initialStreamId)
    private val previousStreamId = MutableStateFlow<Int?>(null)
    private val epgLoading = MutableStateFlow(false)
    private val playerError = MutableStateFlow<String?>(null)
    private val restartUrl = MutableStateFlow<String?>(null)

    private var selectionJob: Job? = null
    private var epgJob: Job? = null
    private val launchQueue = playbackQueue.consume(categoryId, initialStreamId)
    // Freeze this playback session: health/favorites/history updates must not replace TNT variants.
    private val channelList: StateFlow<List<LiveChannel>> = (launchQueue?.let { flowOf(it) }
        ?: channelSource().map { list -> list.filterNot { it.isSectionMarker || it.isHidden } }
            .filter { it.any { channel -> channel.streamId == initialStreamId } }.take(1))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun channelSource() = when (categoryId) {
        GUIDE_TNT -> repository.observeAllChannels().map(::selectTntChannels)
        GUIDE_ALL -> repository.observeAllChannels()
        GUIDE_FAVORITES -> repository.observeFavorites()
        "__recent__" -> repository.observeRecentChannels()
        else -> repository.observeChannels(categoryId)
    }

    private val currentChannel = selectedStreamId.flatMapLatest(repository::observeChannel)
    private val playbackSelection = combine(selectedStreamId, currentChannel) { id, channel -> id to channel }

    private val epgFlow = selectedStreamId.flatMapLatest { streamId ->
        if (streamId > 0) repository.observeEpg(streamId) else flowOf(emptyList())
    }

    private val statusFlow = combine(
        epgLoading,
        playerError,
        previousStreamId,
        restartUrl,
        settingsStore.playerPreferences
    ) { loading, error, previous, restart, prefs ->
        PlayerStatus(loading, error, previous, restart, prefs.bufferPreset, prefs.videoScale, prefs.subtitlesEnabled)
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        repository.savedCredentials,
        channelList,
        playbackSelection,
        epgFlow,
        statusFlow
    ) { credentials, list, selection, epg, status ->
        val (streamId, current) = selection
        val isEpgLoading = status.epgLoading
        val error = status.error
        val previous = status.previousStreamId
        val index = list.indexOfFirst { it.streamId == streamId }
        val channel = current?.takeIf { it.streamId == streamId } ?: list.getOrNull(index)
        val now = System.currentTimeMillis() / 1000L
        val nowProgram = epg.firstOrNull { it.isLiveAt(now) }
        val nextProgram = epg.firstOrNull { it.startTimestamp >= (nowProgram?.stopTimestamp ?: now) }
        PlayerUiState(
            channel = channel,
            streamUrl = if (credentials != null && channel != null) repository.streamUrl(credentials, channel) else null,
            restartUrl = status.restartUrl,
            channelIndex = index,
            channelCount = list.size,
            channels = list,
            nowProgram = nowProgram,
            nextProgram = nextProgram,
            epg = epg,
            epgLoading = isEpgLoading,
            playerError = error,
            previousStreamId = previous,
            bufferPreset = status.bufferPreset,
            videoScale = status.videoScale,
            subtitlesEnabled = status.subtitlesEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    init {
        onChannelSelected(initialStreamId)
    }

    fun nextChannel() = move(+1)

    fun previousChannel() = move(-1)

    fun selectChannel(streamId: Int) {
        if (streamId == selectedStreamId.value || channelList.value.none { it.streamId == streamId }) return
        previousStreamId.value = selectedStreamId.value
        selectedStreamId.value = streamId
        onChannelSelected(streamId)
    }

    fun returnToPreviousChannel() {
        val previous = previousStreamId.value ?: return
        val current = selectedStreamId.value
        previousStreamId.value = current
        selectedStreamId.value = previous
        onChannelSelected(previous)
    }

    fun toggleFavorite() {
        val streamId = uiState.value.channel?.streamId ?: return
        viewModelScope.launch { repository.toggleFavorite(streamId) }
    }

    fun saveVideoScale(value: String) { viewModelScope.launch { settingsStore.setVideoScale(value) } }
    fun saveSubtitlesEnabled(value: Boolean) { viewModelScope.launch { settingsStore.setSubtitlesEnabled(value) } }

    fun reportPlayerError(message: String?) {
        playerError.value = message
    }

    fun reportPlaybackReady() {
        val streamId = selectedStreamId.value
        if (streamId > 0) {
            viewModelScope.launch {
                repository.reportPlaybackReady(streamId)
                val channel = channelList.value.firstOrNull { it.streamId == streamId }
                if (channel != null) {
                    settingsStore.setLastLiveChannel(channel.streamId, channel.name, channel.categoryId)
                }
            }
        }
    }

    fun reportPlaybackFailure(httpCode: Int?, error: String?) {
        val streamId = selectedStreamId.value
        if (streamId > 0) viewModelScope.launch { repository.reportPlaybackFailure(streamId, httpCode, error) }
    }

    fun retryEpg() {
        val streamId = selectedStreamId.value
        if (streamId > 0) refreshEpg(streamId)
    }

    private fun move(delta: Int) {
        val next = adjacentChannelId(channelList.value, selectedStreamId.value, delta) ?: return
        selectChannel(next)
    }

    private fun onChannelSelected(streamId: Int) {
        playerError.value = null
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            repository.markWatched(streamId)
            val channel = repository.observeChannel(streamId).first()
            if (channel != null) {
                settingsStore.setLastLiveChannel(
                    streamId = channel.streamId,
                    name = channel.name,
                    categoryId = channel.categoryId
                )
            }
        }
        refreshEpg(streamId)
    }

    private fun refreshEpg(streamId: Int) {
        epgJob?.cancel()
        restartUrl.value = null
        epgJob = viewModelScope.launch {
            epgLoading.value = true
            try {
                repository.refreshEpg(streamId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Cached EPG remains usable when the provider is unavailable.
            } finally {
                if (selectedStreamId.value == streamId) epgLoading.value = false
            }

            val credentials = repository.savedCredentials.first() ?: return@launch
            val channel = channelList.value.firstOrNull { it.streamId == streamId } ?: return@launch
            val now = System.currentTimeMillis() / 1000L
            val program = repository.observeEpg(streamId).first().firstOrNull { it.isLiveAt(now) } ?: return@launch
            val resolved = repository.resolveCatchupUrl(credentials, channel, program)
            if (selectedStreamId.value == streamId) restartUrl.value = resolved
        }
    }
}
