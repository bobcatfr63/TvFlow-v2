package com.tvflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class TvFlowAiResults(
    val movies: List<VodItem> = emptyList(),
    val series: List<SeriesItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TvFlowAiViewModel @Inject constructor(
    private val repository: XtreamRepository
) : ViewModel() {
    val query = MutableStateFlow("")

    val results = query.debounce(300).mapLatest { raw ->
        if (raw.trim().length < 2) return@mapLatest TvFlowAiResults()
        val parsed = TvFlowIntent.parse(raw)
        if (parsed.terms.isEmpty()) return@mapLatest TvFlowAiResults()
        try {
            withContext(Dispatchers.Default) {
                val movies = LinkedHashMap<Int, VodItem>()
                val series = LinkedHashMap<Int, SeriesItem>()
                for (term in TvFlowIntent.searchTerms(raw).take(8)) {
                    val batch = repository.searchGlobal(term, limitPerType = 70)
                    batch.vod.forEach { movies[it.streamId] = it }
                    batch.series.forEach { series[it.seriesId] = it }
                }
                TvFlowAiResults(
                    movies = if (parsed.seriesOnly && !parsed.filmOnly) emptyList() else movies.values
                        .filter { TvFlowIntent.score(parsed, it.name, it.synopsis, it.releaseYear) > 0 }
                        .sortedByDescending { TvFlowIntent.score(parsed, it.name, it.synopsis, it.releaseYear) }
                        .take(80),
                    series = if (parsed.filmOnly && !parsed.seriesOnly) emptyList() else series.values
                        .filter { TvFlowIntent.score(parsed, it.name, it.plot, it.releaseYear) > 0 }
                        .sortedByDescending { TvFlowIntent.score(parsed, it.name, it.plot, it.releaseYear) }
                        .take(80)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TvFlowAiResults(error = "Recherche indisponible. Vérifie le catalogue local.")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvFlowAiResults())
}
