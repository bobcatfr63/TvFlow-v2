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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
    private val playbackQueue: com.tvflow.app.ui.player.LivePlaybackQueue,
    private val settingsStore: SettingsStore,
    private val smartCache: com.tvflow.app.data.local.SmartCache
) : ViewModel() {
    val categories: StateFlow<List<LiveCategory>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val source = MutableStateFlow<ChannelSource?>(null)
    private val channelSnapshots = mutableMapOf<String, List<LiveChannel>>()
    private var currentProfile: String? = null

    private fun rememberSource(selected: ChannelSource) {
        val value = when (selected) {
            ChannelSource.Tnt -> "__tnt__"
            ChannelSource.Favorites -> "__favorites__"
            ChannelSource.Recent -> "__recent__"
            is ChannelSource.Category -> selected.id
        }
        viewModelScope.launch {
            repository.activeProfileId.first()?.let { smartCache.saveLastCategory(it, "DIRECT", value) }
        }
    }

    val selectedSource: StateFlow<ChannelSource?> = source
    private val _pinRequiredCategory = MutableStateFlow<LiveCategory?>(null)
    val pinRequiredCategory: StateFlow<LiveCategory?> = _pinRequiredCategory
    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError

    private val sort = MutableStateFlow(HomeSort.NAME)
    val selectedSort: StateFlow<HomeSort> = sort
    private val subFilterId = MutableStateFlow<String?>(null)
    val selectedSubFilterId: StateFlow<String?> = subFilterId

    private val channelLoadState = combine(source, subFilterId, repository.activeProfileId) { selected, filterId, profile ->
        Triple(selected, filterId, profile)
    }.flatMapLatest { (selected, filterId, profile) ->
        val key = "$profile|$selected|$filterId"
        val stream = when (selected) {
            ChannelSource.Tnt -> repository.observeAllChannels().map { selectTntChannels(it) }
            ChannelSource.Favorites -> repository.observeFavorites()
            ChannelSource.Recent -> repository.observeRecentChannels()
            is ChannelSource.Category -> repository.observeChannels(filterId ?: selected.id)
            null -> flowOf(emptyList())
        }
        stream.map { list ->
            if (profile != null && selected != null) channelSnapshots[key] = list
            list as List<LiveChannel>?
        }.onStart { emit(channelSnapshots[key]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isLoading: StateFlow<Boolean> = channelLoadState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val channels: StateFlow<List<LiveChannel>> = combine(channelLoadState, sort) { list, order ->
        if (source.value == ChannelSource.Tnt) list.orEmpty() else sortHomeChannels(list.orEmpty(), order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    init {
        viewModelScope.launch {
            combine(repository.activeProfileId, categories) { profile, available -> profile to available }
                .collect { (profile, available) ->
                    if (profile != currentProfile) {
                        currentProfile = profile
                        channelSnapshots.clear()
                        source.value = null
                        subFilterId.value = null
                        _pinRequiredCategory.value = null
                        _pinError.value = null
                    }
                    if (profile != null && source.value == null && available.isNotEmpty()) {
                        val saved = smartCache.lastCategory(profile, "DIRECT")
                        source.value = when (saved) {
                            "__tnt__" -> ChannelSource.Tnt
                            "__favorites__" -> ChannelSource.Favorites
                            "__recent__" -> ChannelSource.Recent
                            else -> available.firstOrNull { it.id == saved && !it.isLocked }?.let { ChannelSource.Category(it.id) }
                        } ?: ChannelSource.Category(available.firstOrNull { !it.isLocked }?.id ?: return@collect)
                    }
                }
        }
    }

    private val focusedChannelId = MutableStateFlow<Int?>(null)
    val previewChannel: StateFlow<LiveChannel?> = combine(channels, focusedChannelId) { items, id ->
        items.firstOrNull { it.streamId == id && !it.isSectionMarker }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val previewUrl: StateFlow<String?> = combine(previewChannel, repository.savedCredentials) { channel, credentials ->
        if (channel == null || credentials == null) null else repository.streamUrl(credentials, channel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun focusChannel(id: Int?) { focusedChannelId.value = id }

    fun capturePlayback(sourceId: String) { playbackQueue.capture(sourceId, channels.value) }

    fun selectCategory(id: String) {
        val category = categories.value.firstOrNull { it.id == id }
        if (category?.isLocked == true) {
            _pinRequiredCategory.value = category
            _pinError.value = null
        } else {
            subFilterId.value = null
            source.value = ChannelSource.Category(id)
            rememberSource(ChannelSource.Category(id))
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
                rememberSource(ChannelSource.Category(category.id))
            } else _pinError.value = "PIN incorrect"
        }
    }

    fun dismissParentalPin() { _pinRequiredCategory.value = null; _pinError.value = null }

    fun selectTnt() {
        subFilterId.value = null
        source.value = ChannelSource.Tnt
        rememberSource(ChannelSource.Tnt)
    }

    fun selectFavorites() {
        subFilterId.value = null
        source.value = ChannelSource.Favorites
        rememberSource(ChannelSource.Favorites)
    }

    fun selectRecent() {
        subFilterId.value = null
        source.value = ChannelSource.Recent
        rememberSource(ChannelSource.Recent)
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
        HomeSort.NAME -> visible.sortedWith(compareBy<LiveChannel> { it.customOrder ?: Int.MAX_VALUE }
            .thenBy { channelAlphabeticalKey(it.name) })
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
