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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import android.util.Log
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class MediaFilter(
    val categoryId: String?,
    val year: Int?,
    val month: Int?,
    val sort: String
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    // Categories already reflect the user's choices in Settings.
    // Show all visible categories directly, without a second France/Kids/Other filter.
    val categories: StateFlow<List<MediaCategory>> = allCategories

    val searchQuery = MutableStateFlow("")
    private val searched = searchQuery.debounce(300).map { it.trim() }.distinctUntilChanged()

    val searchedVod: StateFlow<List<VodItem>> = searched.flatMapLatest { query ->
        if (type == MediaType.VOD && query.length >= 2) repository.searchVod(query)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchedSeries: StateFlow<List<SeriesItem>> = searched.flatMapLatest { query ->
        if (type == MediaType.SERIES && query.length >= 2) repository.searchSeries(query)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = selectedCategory

    val sortOrder = MutableStateFlow("DATE")
    fun selectSort(value: String) { if (value in setOf("DATE", "AZ")) sortOrder.value = value }

    private val selectedYearState = MutableStateFlow<Int?>(null)
    val selectedYear: StateFlow<Int?> = selectedYearState

    private val selectedMonthState = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = selectedMonthState

    // Date filters span all categories of the active profile.
    val releaseYears: StateFlow<List<Int>> = repository.observeReleaseYears(type, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val releaseMonths: StateFlow<List<Int>> = selectedYearState.flatMapLatest { year ->
            if (year == null) flowOf(emptyList())
            else repository.observeReleaseMonths(type, null, year)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filter = combine(
        selectedCategory,
        selectedYearState,
        selectedMonthState,
        sortOrder
    ) { categoryId, year, month, sort ->
        MediaFilter(categoryId, year, month, sort)
    }

    val vod: Flow<PagingData<VodItem>> = filter.flatMapLatest { filter ->
        if (type == MediaType.VOD && filter.categoryId != null) {
            repository.pagedVod(if (filter.year != null) null else filter.categoryId, filter.year, filter.month, filter.sort)
        } else {
            flowOf(PagingData.empty())
        }
    }.cachedIn(viewModelScope)

    val series: Flow<PagingData<SeriesItem>> = filter.flatMapLatest { filter ->
        if (type == MediaType.SERIES && filter.categoryId != null) {
            repository.pagedSeries(if (filter.year != null) null else filter.categoryId, filter.year, filter.month, filter.sort)
        } else {
            flowOf(PagingData.empty())
        }
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            // After a backup restore, credentials exist but media tables may be empty.
            // Synchronize only when the library is opened, never at every app startup.
            if (repository.observeMediaCategories(type).first().isEmpty()) {
                Log.i("TVFlowMedia", "Media categories missing on library open; refresh requested")
                runCatching { repository.refreshMediaCatalog() }
                    .onSuccess { Log.i("TVFlowMedia", "Media catalog synchronization completed") }
                    .onFailure { Log.w("TVFlowMedia", "Media catalog synchronization failed: ${it.javaClass.simpleName}") }
            }
        }
        viewModelScope.launch {
            categories.collect { visible ->
                if (selectedCategory.value != null && visible.none { it.id == selectedCategory.value }) {
                    selectedCategory.value = visible.firstOrNull()?.id
                    selectedYearState.value = null
                    selectedMonthState.value = null
                }
            }
        }
    }

    fun selectCategory(id: String) {
        if (selectedCategory.value == id) return
        selectedCategory.value = id
        // Retain date filtering when browsing categories: date results remain global.
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
