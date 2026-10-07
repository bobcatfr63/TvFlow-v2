package com.tvflow.app.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.MediaCategory
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class MediaFilter(
    val categoryId: String?,
    val year: Int?,
    val month: Int?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    private val repository: XtreamRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val type: MediaType = runCatching {
        MediaType.valueOf(savedStateHandle.get<String>("type") ?: MediaType.VOD.name)
    }.getOrDefault(MediaType.VOD)

    private val allCategories: StateFlow<List<MediaCategory>> = repository.observeMediaCategories(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedSectionState = MutableStateFlow(MediaSection.FRANCE)
    val selectedSection: StateFlow<MediaSection> = selectedSectionState

    val availableSections: StateFlow<List<MediaSection>> = allCategories
        .map { categories ->
            MediaSection.entries.filter { section -> categories.any { it.section == section } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<MediaCategory>> = combine(
        allCategories,
        selectedSectionState
    ) { categories, section ->
        categories.filter { it.section == section }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = selectedCategory

    private val selectedYearState = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = selectedYearState

    private val selectedMonthState = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = selectedMonthState

    val releaseYears: StateFlow<List<Int>> = selectedCategory.flatMapLatest { categoryId ->
        if (categoryId == null) flowOf(emptyList())
        else repository.observeReleaseYears(type, categoryId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val releaseMonths: StateFlow<List<Int>> = combine(
        selectedCategory,
        selectedYearState
    ) { categoryId, year -> categoryId to year }
        .flatMapLatest { (categoryId, year) ->
            if (categoryId == null || year == null) flowOf(emptyList())
            else repository.observeReleaseMonths(type, categoryId, year)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filter = combine(
        selectedCategory,
        selectedYearState,
        selectedMonthState
    ) { categoryId, year, month ->
        MediaFilter(categoryId, year, month)
    }

    val vod: Flow<PagingData<VodItem>> = filter.flatMapLatest { filter ->
        if (type == MediaType.VOD && filter.categoryId != null) {
            repository.pagedVod(filter.categoryId, filter.year, filter.month)
        } else {
            flowOf(PagingData.empty())
        }
    }.cachedIn(viewModelScope)

    val series: Flow<PagingData<SeriesItem>> = filter.flatMapLatest { filter ->
        if (type == MediaType.SERIES && filter.categoryId != null) {
            repository.pagedSeries(filter.categoryId, filter.year, filter.month)
        } else {
            flowOf(PagingData.empty())
        }
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            availableSections.collect { sections ->
                if (sections.isNotEmpty() && selectedSectionState.value !in sections) {
                    selectedSectionState.value = sections.first()
                    selectedCategory.value = null
                }
            }
        }
    }

    fun selectSection(section: MediaSection) {
        if (selectedSectionState.value == section) return
        selectedSectionState.value = section
        selectedCategory.value = null
        selectedYearState.value = null
        selectedMonthState.value = null
    }

    fun selectCategory(id: String) {
        if (selectedCategory.value == id) return
        selectedCategory.value = id
        selectedYearState.value = null
        selectedMonthState.value = null
        enrichCategory(id)
    }

    fun selectYear(year: Int?) {
        selectedYearState.value = year
        selectedMonthState.value = null
    }

    fun selectMonth(month: Int?) {
        selectedMonthState.value = month
    }

    fun refresh() {
        viewModelScope.launch { runCatching { repository.refreshMediaCatalog() } }
    }

    private fun enrichCategory(categoryId: String) {
        viewModelScope.launch {
            runCatching {
                repository.enrichMissingMediaMetadata(type, categoryId, limit = if (type == MediaType.VOD) 60 else 24)
            }
        }
    }
}
