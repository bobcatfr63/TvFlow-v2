package com.tvflow.app.ui.guide

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.data.repository.channelAlphabeticalKey
import com.tvflow.app.data.repository.sortTntChannels
import com.tvflow.app.data.repository.selectTntChannels
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveCategory
import com.tvflow.app.domain.LiveChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val WINDOW_SECONDS = 2 * 60 * 60L
private const val STEP_SECONDS = 30 * 60L
const val GUIDE_ALL = "__all__"
const val GUIDE_FAVORITES = "__favorites__"
const val GUIDE_TNT = "__tnt__"

enum class GuideSort { NAME, FAVORITES, EPG }

data class GuideUiState(
    val categories: List<LiveCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val channels: List<LiveChannel> = emptyList(),
    val selectedChannel: LiveChannel? = null,
    val previewChannel: LiveChannel? = null,
    val previewStreamUrl: String? = null,
    val selectedProgram: EpgProgram? = null,
    val programs: List<EpgProgram> = emptyList(),
    val timelinePrograms: Map<Int, List<EpgProgram>> = emptyMap(),
    val windowStart: Long = currentHalfHour(),
    val windowEnd: Long = currentHalfHour() + WINDOW_SECONDS,
    val loading: Boolean = false,
    val pinRequiredCategory: LiveCategory? = null,
    val pinError: String? = null,
    val sort: GuideSort = GuideSort.NAME,
    val epgOnly: Boolean = false,
    val selectedSubFilterId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val selectedSubFilterId = MutableStateFlow<String?>(null)
    private val selectedStreamId = MutableStateFlow<Int?>(null)
    private val previewStreamId = MutableStateFlow<Int?>(null)
    private val selectedProgramStart = MutableStateFlow<Long?>(null)
    private val visibleEpgStreamIds = MutableStateFlow<List<Int>>(emptyList())
    private val loading = MutableStateFlow(false)
    private val windowStart = MutableStateFlow(currentHalfHour())
    private val pinRequiredCategory = MutableStateFlow<LiveCategory?>(null)
    private val pinError = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(GuideSort.NAME)
    private val epgOnly = MutableStateFlow(false)

    private val categories = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val channels = combine(selectedCategoryId, selectedSubFilterId) { id, filterId ->
        id to filterId
    }.flatMapLatest { (id, filterId) ->
        Log.d("TVFlowGuide", "channelFlow category=$id filter=$filterId")
        channelFlow(id, filterId)
    }.onEach { Log.d("TVFlowGuide", "channels=${it.size} first=${it.firstOrNull()?.name}") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val previewChannel = previewStreamId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeChannel(id)
    }

    private val selectedPrograms = selectedStreamId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeEpg(id)
    }

    private val timelinePrograms = combine(
        channels,
        visibleEpgStreamIds,
        selectedStreamId
    ) { channelList, visibleIds, selectedId ->
        resolveTimelineStreamIds(channelList, visibleIds, selectedId)
    }.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList()) else repository.observeEpgForStreams(ids)
    }

    private val core = combine(
        categories,
        selectedCategoryId,
        channels,
        selectedStreamId,
        selectedPrograms
    ) { cats, catId, channelList, streamId, epg ->
        GuideUiState(
            categories = cats,
            selectedCategoryId = catId,
            channels = channelList,
            selectedChannel = channelList.firstOrNull { it.streamId == streamId },
            programs = epg
        )
    }

    private val content = combine(core, timelinePrograms, windowStart, selectedProgramStart) { base, timeline, start, selectedStart ->
        val grouped = timeline.groupBy { it.streamId }
        val channel = base.selectedChannel
        val channelPrograms = channel?.let { grouped[it.streamId].orEmpty() }.orEmpty()
        val now = System.currentTimeMillis() / 1000L
        val selected = channelPrograms.firstOrNull { it.startTimestamp == selectedStart }
            ?: channelPrograms.firstOrNull { it.isLiveAt(now) }
            ?: channelPrograms.firstOrNull { it.stopTimestamp > now }
            ?: channelPrograms.firstOrNull()

        base.copy(
            selectedProgram = selected,
            timelinePrograms = grouped,
            windowStart = start,
            windowEnd = start + WINDOW_SECONDS
        )
    }

    private val contentWithPreview = combine(
        content,
        previewChannel,
        repository.savedCredentials
    ) { value, preview, credentials ->
        value.copy(
            previewChannel = preview,
            previewStreamUrl = if (credentials != null && preview != null) {
                repository.streamUrl(credentials, preview)
            } else null
        )
    }

    private val presented = combine(contentWithPreview, sort, epgOnly, selectedSubFilterId) { value, order, onlyEpg, filterId ->
        val loadedEpgStreamIds = value.timelinePrograms.keys
        fun LiveChannel.hasEpg(): Boolean =
            !epgChannelId.isNullOrBlank() || streamId in loadedEpgStreamIds

        val visible = if (onlyEpg) {
            value.channels.filter { it.hasEpg() || it.isSectionMarker }
        } else {
            value.channels
        }
        val ordered = if (value.selectedCategoryId == GUIDE_TNT) {
            visible.sortedBy { com.tvflow.app.data.repository.frenchTntNumber(
                com.tvflow.app.data.repository.canonicalFrenchChannelName(it.name)
            ) ?: Int.MAX_VALUE }
        } else when (order) {
            GuideSort.NAME -> visible.filterNot { it.isSectionMarker }.sortedBy { channelAlphabeticalKey(it.name) }
            GuideSort.FAVORITES -> visible.filterNot { it.isSectionMarker }
                .sortedWith(compareByDescending<LiveChannel> { it.isFavorite }.thenBy { channelAlphabeticalKey(it.name) })
            GuideSort.EPG -> visible.filterNot { it.isSectionMarker }
                .sortedWith(compareByDescending<LiveChannel> { it.hasEpg() }.thenBy { channelAlphabeticalKey(it.name) })
        }
        value.copy(
            channels = ordered,
            sort = order,
            epgOnly = onlyEpg,
            selectedSubFilterId = filterId
        )
    }

    val state: StateFlow<GuideUiState> = combine(presented, loading, pinRequiredCategory, pinError) { value, busy, locked, error ->
        value.copy(loading = busy, pinRequiredCategory = locked, pinError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideUiState())

    init {
        viewModelScope.launch {
            categories.collect { cats ->
                if (selectedCategoryId.value == null && cats.isNotEmpty()) selectCategory(GUIDE_TNT)
            }
        }
        viewModelScope.launch {
            repository.activeProfileId.drop(1).collect {
                selectedCategoryId.value = null
                selectedSubFilterId.value = null
                selectedStreamId.value = null
                selectedProgramStart.value = null
                visibleEpgStreamIds.value = emptyList()
                previewStreamId.value = null
                pinRequiredCategory.value = null
                pinError.value = null
                selectCategory(GUIDE_TNT)
            }
        }
    }

    fun cycleSort() { sort.value = GuideSort.entries[(sort.value.ordinal + 1) % GuideSort.entries.size] }
    fun toggleEpgOnly() { epgOnly.value = !epgOnly.value }

    fun selectSubFilter(id: String?) {
        if (selectedSubFilterId.value == id) return
        selectedSubFilterId.value = id
        selectedStreamId.value = null
        selectedProgramStart.value = null
        visibleEpgStreamIds.value = emptyList()
        previewStreamId.value = null
        val categoryId = selectedCategoryId.value
        viewModelScope.launch {
            val list = channelFlow(categoryId, id).first()
            if (selectedCategoryId.value == categoryId && selectedSubFilterId.value == id) {
                list.firstOrNull()?.let { selectChannel(it.streamId) }
            }
        }
    }

    fun selectCategory(id: String) {
        if (selectedCategoryId.value == id) return
        val category = categories.value.firstOrNull { it.id == id }
        if (category?.isLocked == true) {
            pinRequiredCategory.value = category
            pinError.value = null
            return
        }
        applyCategory(id)
    }

    fun confirmParentalPin(pin: String) {
        val category = pinRequiredCategory.value ?: return
        viewModelScope.launch {
            if (settingsStore.verifyParentalPin(pin)) {
                pinRequiredCategory.value = null
                pinError.value = null
                applyCategory(category.id)
            } else pinError.value = "PIN incorrect"
        }
    }

    fun dismissParentalPin() { pinRequiredCategory.value = null; pinError.value = null }

    private fun applyCategory(id: String) {
        selectedCategoryId.value = id
        selectedSubFilterId.value = null
        selectedStreamId.value = null
        selectedProgramStart.value = null
        visibleEpgStreamIds.value = emptyList()
        viewModelScope.launch {
            val list = channelFlow(id, null).first()
            if (selectedCategoryId.value == id && selectedSubFilterId.value == null) {
                list.firstOrNull()?.let { selectChannel(it.streamId) }
            }
        }
    }

    private fun channelFlow(categoryId: String?, filterId: String?): Flow<List<LiveChannel>> = when (categoryId) {
        null -> flowOf(emptyList())
        GUIDE_ALL -> repository.observeAllChannels()
        GUIDE_FAVORITES -> repository.observeFavorites()
        GUIDE_TNT -> repository.observeAllChannels().map { channels ->
            selectTntChannels(channels)
        }
        else -> repository.observeChannels(filterId ?: categoryId)
    }

    fun selectChannel(streamId: Int) {
        if (channels.value.firstOrNull { it.streamId == streamId }?.isSectionMarker == true) return
        if (selectedStreamId.value != streamId) selectedProgramStart.value = null
        selectedStreamId.value = streamId
        viewModelScope.launch { runCatching { repository.refreshEpg(streamId, 16) } }
    }

    fun selectProgram(streamId: Int, startTimestamp: Long) {
        selectedStreamId.value = streamId
        selectedProgramStart.value = startTimestamp
    }

    fun previewChannel(streamId: Int) {
        if (channels.value.firstOrNull { it.streamId == streamId }?.isSectionMarker == true) return
        if (previewStreamId.value == streamId) return
        previewStreamId.value = streamId
        if (selectedStreamId.value != streamId) selectChannel(streamId)
    }

    fun previousWindow() {
        windowStart.value -= STEP_SECONDS
    }

    fun nextWindow() {
        windowStart.value += STEP_SECONDS
    }

    fun jumpToNow() {
        windowStart.value = currentHalfHour()
    }

    fun prefetchVisibleEpg(streamIds: List<Int>) {
        val ids = streamIds.distinct().take(20)
        visibleEpgStreamIds.value = ids
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.prefetchEpg(
                    streamIds = ids,
                    limit = 16,
                    parallelism = 3
                )
            }
        }
    }
}

internal fun resolveTimelineStreamIds(
    channels: List<LiveChannel>,
    visibleIds: List<Int>,
    selectedId: Int?,
    maxCount: Int = 32
): List<Int> {
    if (channels.isEmpty() || maxCount <= 0) return emptyList()
    val available = channels
        .asSequence()
        .filterNot { it.isSectionMarker }
        .map { it.streamId }
        .toSet()

    val requested = buildList {
        visibleIds.forEach { if (it in available) add(it) }
        if (selectedId != null && selectedId in available) add(selectedId)
        if (isEmpty()) {
            channels.asSequence()
                .filterNot { it.isSectionMarker }
                .take(12)
                .forEach { add(it.streamId) }
        }
    }

    return requested.distinct().take(maxCount)
}

private fun currentHalfHour(): Long {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.MINUTE, if (get(Calendar.MINUTE) < 30) 0 else 30)
    }
    return calendar.timeInMillis / 1000L
}

