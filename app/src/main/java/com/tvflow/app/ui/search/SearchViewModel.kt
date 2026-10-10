package com.tvflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.GlobalSearchResults
import com.tvflow.app.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: XtreamRepository
) : ViewModel() {
    val query = MutableStateFlow("")

    val results: StateFlow<GlobalSearchResults> = query
        .debounce(180)
        .distinctUntilChanged()
        .flatMapLatest { raw ->
            val q = raw.trim()
            if (q.length < 2) {
                flowOf(GlobalSearchResults())
            } else {
                combine(
                    repository.searchLive(q),
                    repository.searchVod(q),
                    repository.searchSeries(q)
                ) { live, vod, series ->
                    GlobalSearchResults(live = live, vod = vod, series = series)
                }.catch { emit(GlobalSearchResults()) }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GlobalSearchResults()
        )
}
