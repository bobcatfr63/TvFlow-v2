package com.tvflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.SeriesItem
import java.util.Calendar
import kotlinx.coroutines.flow.first
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
    val programs: List<Pair<EpgProgram, LiveChannel>> = emptyList(),
    val tvQuestion: Boolean = false,
    val notice: String? = null,
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
        val tvQuestion = TvProgramMatcher.isTvQuestion(raw)
        val requestedPerson = TvFlowIntent.requestedPerson(raw)
        if (parsed.terms.isEmpty() && !tvQuestion) return@mapLatest TvFlowAiResults()
        try {
            withContext(Dispatchers.Default) {
                val movies = LinkedHashMap<Int, VodItem>()
                val series = LinkedHashMap<Int, SeriesItem>()
                // Live-TV requests must not wait for sequential VOD catalog searches.
                if (!tvQuestion) for (term in TvFlowIntent.searchTerms(raw).take(8)) {
                    val batch = repository.searchGlobal(term, limitPerType = 70)
                    batch.vod.forEach { movies[it.streamId] = it }
                    batch.series.forEach { series[it.seriesId] = it }
                }
                val programs = if (tvQuestion) {
                    val (start, end) = TvProgramMatcher.window(raw)
                    val channels = repository.observeAllChannels().first().associateBy { it.streamId }
                    repository.searchEpgWindow(start, end).asSequence()
                        .mapNotNull { program ->
                            val channel = channels[program.streamId] ?: return@mapNotNull null
                            val rank = TvProgramMatcher.score(raw, program)
                            if (rank > 0) Triple(program, channel, rank) else null
                        }
                        .sortedWith(compareByDescending<Triple<EpgProgram, LiveChannel, Int>> { it.third }
                            .thenBy { it.first.startTimestamp })
                        .take(40).map { it.first to it.second }.toList()
                } else emptyList()
                val personCheck = requestedPerson?.let { person ->
                    val normalizedPerson = person.lowercase()
                    val confirmedMovies = movies.values.filter {
                        (it.synopsis.orEmpty() + " " + it.name).lowercase().contains(normalizedPerson)
                    }
                    val confirmedSeries = series.values.filter {
                        (it.plot.orEmpty() + " " + it.name).lowercase().contains(normalizedPerson)
                    }
                    confirmedMovies to confirmedSeries
                }
                TvFlowAiResults(
                    notice = if (requestedPerson != null && personCheck?.first?.isEmpty() == true && personCheck.second.isEmpty())
                        "Impossible de confirmer $requestedPerson dans les métadonnées du catalogue actif. Aucun résultat approximatif n'est présenté comme vérifié."
                        else null,
                    programs = programs,
                    tvQuestion = tvQuestion,
                    movies = if (parsed.seriesOnly && !parsed.filmOnly) emptyList() else (personCheck?.first ?: movies.values)
                        .filter { TvFlowIntent.score(parsed, it.name, it.synopsis, it.releaseYear) > 0 }
                        .sortedByDescending { TvFlowIntent.score(parsed, it.name, it.synopsis, it.releaseYear) }
                        .take(80),
                    series = if (parsed.filmOnly && !parsed.seriesOnly) emptyList() else (personCheck?.second ?: series.values)
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
