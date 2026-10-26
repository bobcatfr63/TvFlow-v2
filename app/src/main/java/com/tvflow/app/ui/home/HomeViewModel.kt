package com.tvflow.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.data.repository.channelAlphabeticalKey
import com.tvflow.app.data.repository.sortTntChannels
import com.tvflow.app.data.repository.selectTntChannels
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.domain.LiveCategory
import com.tvflow.app.domain.LiveChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChannelSource {
    data object Tnt : ChannelSource
    data object Favorites : ChannelSource
    data object Recent : ChannelSource
    data class Category(val id: String) : ChannelSource
}

enum class HomeSort {
    NAME,
    FAVORITES,
    EPG
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val settingsStore: SettingsStore,
    private val playbackQueue: com.tvflow.app.ui.player.LivePlaybackQueue
) : ViewModel() {
    fun capturePlaybackQueue(sourceId: String) {
        playbackQueue.capture(sourceId, channels.value)
    }
    val categories: StateFlow<List<LiveCategory>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val previewId = MutableStateFlow<Int?>(null)
    val previewChannel = combine(previewId, repository.savedCredentials, repository.observeAllChannels()) { id, credentials, channels ->
        if (id == null || credentials == null) null else channels.firstOrNull { it.streamId == id }?.let { channel ->
            channel to repository.streamUrl(credentials, channel)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previewChannel(id: Int) { previewId.value = id }
    fun clearPreview() { previewId.value = null }

    private val source = MutableStateFlow<ChannelSource?>(null)
    val selectedSource: StateFlow<ChannelSource?> = source
    private val _pinRequiredCategory = MutableStateFlow<LiveCategory?>(null)
    val pinRequiredCategory: StateFlow<LiveCategory?> = _pinRequiredCategory
    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val sort = MutableStateFlow(HomeSort.NAME)
    val selectedSort: StateFlow<HomeSort> = sort
    private val subFilterId = MutableStateFlow<String?>(null)
    val selectedSubFilterId: StateFlow<String?> = subFilterId

    private val channelLoadState = combine(source, subFilterId) { selected, filterId ->
        selected to filterId
    }.flatMapLatest { (selected, filterId) ->
        val stream = when (selected) {
            ChannelSource.Tnt -> repository.observeAllChannels().map { channels ->
                selectTntChannels(channels)
            }
            ChannelSource.Favorites -> repository.observeFavorites()
            ChannelSource.Recent -> repository.observeRecentChannels()
            is ChannelSource.Category -> repository.observeChannels(filterId ?: selected.id)
            null -> flowOf(emptyList())
        }
        stream.map { it as List<LiveChannel>? }
            .onStart { emit(null) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isLoading: StateFlow<Boolean> = channelLoadState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val channels: StateFlow<List<LiveChannel>> = combine(channelLoadState, sort) { list, order ->
        if (source.value == ChannelSource.Tnt) list.orEmpty() else sortHomeChannels(list.orEmpty(), order)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    init {
        viewModelScope.launch {
            repository.activeProfileId.drop(1).collect {
                source.value = null
                _pinRequiredCategory.value = null
                _pinError.value = null
            }
        }
    }

    fun selectCategory(id: String) {
        val category = categories.value.firstOrNull { it.id == id }
        if (category?.isLocked == true) {
            _pinRequiredCategory.value = category
            _pinError.value = null
        } else {
            subFilterId.value = null
            source.value = ChannelSource.Category(id)
        }
    }

    fun confirmParentalPin(pin: String) {
        val category = _pinRequiredCategory.value ?: return
        viewModelScope.launch {
            if (settingsStore.verifyParentalPin(pin)) {
                _pinRequiredCategory.value = null
                _pinError.value = null
                subFilterId.value = null
                source.value = ChannelSource.Category(category.id)
            } else _pinError.value = "PIN incorrect"
        }
    }

    fun dismissParentalPin() { _pinRequiredCategory.value = null; _pinError.value = null }

    fun selectTnt() {
        subFilterId.value = null
        source.value = ChannelSource.Tnt
    }

    fun selectFavorites() {
        subFilterId.value = null
        source.value = ChannelSource.Favorites
    }

    fun selectRecent() {
        subFilterId.value = null
        source.value = ChannelSource.Recent
    }

    fun selectSubFilter(id: String?) {
        subFilterId.value = id
    }

    fun cycleSort() {
        sort.value = HomeSort.entries[(sort.value.ordinal + 1) % HomeSort.entries.size]
    }

    fun toggleFavorite(streamId: Int) {
        viewModelScope.launch { repository.toggleFavorite(streamId) }
    }

    fun refresh() {
        viewModelScope.launch { runCatching { repository.refreshCatalog() } }
    }
}

internal fun sortHomeChannels(
    list: List<LiveChannel>,
    order: HomeSort
): List<LiveChannel> {
    val visible = list.filterNot { it.isSectionMarker }
    return when (order) {
        HomeSort.NAME -> visible.sortedBy { channelAlphabeticalKey(it.name) }
        HomeSort.FAVORITES -> visible.sortedWith(
            compareByDescending<LiveChannel> { it.isFavorite }
                .thenBy { channelAlphabeticalKey(it.name) }
        )
        HomeSort.EPG -> visible.sortedWith(
            compareByDescending<LiveChannel> { !it.epgChannelId.isNullOrBlank() }
                .thenBy { channelAlphabeticalKey(it.name) }
        )
    }
}
