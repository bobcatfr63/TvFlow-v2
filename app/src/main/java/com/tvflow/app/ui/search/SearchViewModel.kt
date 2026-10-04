package com.tvflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(repository: XtreamRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val live: StateFlow<List<LiveChannel>> = query.flatMapLatest { q -> if (q.trim().length >= 2) repository.searchLive(q.trim()) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vod: StateFlow<List<VodItem>> = query.flatMapLatest { q -> if (q.trim().length >= 2) repository.searchVod(q.trim()) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val series: StateFlow<List<SeriesItem>> = query.flatMapLatest { q -> if (q.trim().length >= 2) repository.searchSeries(q.trim()) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
