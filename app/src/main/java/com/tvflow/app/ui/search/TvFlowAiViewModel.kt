package com.tvflow.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.data.metadata.TmdbMetadataClient
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.MediaType
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
    val searchedQuery: String = "",
    val movies: List<VodItem> = emptyList(),
    val series: List<SeriesItem> = emptyList(),
    val programs: List<Pair<EpgProgram, LiveChannel>> = emptyList(),
    val tvQuestion: Boolean = false,
    val footballAnswer: FootballAnswer? = null,
    val notice: String? = null,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TvFlowAiViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val metadata: TmdbMetadataClient,
    private val sports: FootballScheduleClient
) : ViewModel() {
    val query = MutableStateFlow("")

    val results = query.debounce(300).mapLatest { raw ->
        if (raw.trim().length < 2) return@mapLatest TvFlowAiResults(searchedQuery = raw)
        val parsed = TvFlowIntent.parse(raw)
        val similarityReference = TvFlowSimilarity.referenceTitle(raw)
        val tvQuestion = TvProgramMatcher.isTvQuestion(raw)
        val requestedPerson = TvFlowIntent.requestedPerson(raw)
        if (parsed.terms.isEmpty() && !tvQuestion && similarityReference == null) return@mapLatest TvFlowAiResults(searchedQuery = raw)
        try {
            // Catalog, EPG and metadata lookups may block on network or database I/O.
            // Keep them off the CPU-bound Default dispatcher.
            withContext(Dispatchers.IO) {
                if (similarityReference != null) {
                    val referenceCandidates = repository.searchGlobal(similarityReference, limitPerType = 35).vod
                    val reference = referenceCandidates.firstOrNull { item ->
                        TvFlowSimilarity.normalize(item.name).contains(similarityReference)
                    }
                    val terms = TvFlowSimilarity.keywords(reference?.synopsis)
                    if (reference == null || terms.size < 2) {
                        return@withContext TvFlowAiResults(
                            searchedQuery = raw,
                            notice = "Impossible de proposer des films similaires : film de référence ou synopsis indisponible dans le catalogue actif."
                        )
                    }
                    val candidates = LinkedHashMap<Int, VodItem>()
                    for (term in terms) {
                        repository.searchGlobal(term, limitPerType = 70).vod.forEach { candidate ->
                            if (candidate.streamId != reference.streamId) candidates[candidate.streamId] = candidate
                        }
                    }
                    val matches = candidates.values
                        .map { it to TvFlowSimilarity.overlap(terms, it.name, it.synopsis) }
                        .filter { it.second >= 2 }
                        .sortedWith(compareByDescending<Pair<VodItem, Int>> { it.second }
                            .thenByDescending { it.first.releaseYear ?: 0 })
                        .take(40).map { it.first }
                    return@withContext TvFlowAiResults(
                        searchedQuery = raw,
                        movies = matches,
                        notice = if (matches.isEmpty()) "Aucun film similaire confirmé dans ton catalogue pour ce synopsis."
                            else "Suggestions selon les thèmes communs avec ${reference.name}, disponibles sur ton serveur."
                    )
                }
                val sportsTeam = TvProgramMatcher.requestedTeam(raw)
                val footballAnswer = if (sportsTeam != null && TvProgramMatcher.normalize(raw).contains("soir") && (TvProgramMatcher.normalize(raw).contains("match") || TvProgramMatcher.normalize(raw).contains("joue") || TvProgramMatcher.normalize(raw).contains("foot"))) {
                    runCatching { sports.teamTonight(sportsTeam) }.getOrElse {
                        FootballAnswer("Calendrier sportif momentanément inaccessible. Match non confirmé.", sourceUrl = "https://www.thesportsdb.com/")
                    }
                } else null
                val movies = LinkedHashMap<Int, VodItem>()
                val series = LinkedHashMap<Int, SeriesItem>()
                val movieCategories = repository.observeMediaCategories(MediaType.VOD).first()
                val seriesCategories = repository.observeMediaCategories(MediaType.SERIES).first()
                val movieCategoryNames = movieCategories.associate { it.id to it.name }
                val seriesCategoryNames = seriesCategories.associate { it.id to it.name }
                // Live-TV requests must not wait for sequential VOD catalog searches.
                val verifiedCredits = if (requestedPerson != null && !tvQuestion) {
                    runCatching { metadata.movieCreditsForPerson(requestedPerson) }.getOrNull()
                } else null
                val searchQueries = if (requestedPerson != null) verifiedCredits.orEmpty().map { it.first }.take(24)
                    else TvFlowIntent.searchTerms(raw).take(8)
                if (!tvQuestion) for (term in searchQueries) {
                    val batch = repository.searchGlobal(term, limitPerType = 70)
                    batch.vod.forEach { movies[it.streamId] = it }
                    batch.series.forEach { series[it.seriesId] = it }
                }
                // Provider categories can identify a genre even when the title and plot do not.
                // The supplemental Room queries are limited to 8 categories and 120 items per type.
                if (!tvQuestion && requestedPerson == null && parsed.terms.isNotEmpty()) {
                    val vodIds = movieCategories.filter {
                        TvFlowIntent.score(parsed, "", null, null, it.name) > 0
                    }.map { it.id }.take(8)
                    val seriesIds = seriesCategories.filter {
                        TvFlowIntent.score(parsed, "", null, null, it.name) > 0
                    }.map { it.id }.take(8)
                    if (vodIds.isNotEmpty() || seriesIds.isNotEmpty()) {
                        val categoryResults = repository.searchAiCategoryCandidates(vodIds, seriesIds)
                        categoryResults.vod.forEach { movies[it.streamId] = it }
                        categoryResults.series.forEach { series[it.seriesId] = it }
                    }
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
                val personCheck = requestedPerson?.let { _ ->
                    val credits = verifiedCredits.orEmpty()
                    val confirmedMovies = movies.values.filter { movie ->
                        credits.any { (title, year) -> TvFlowIntent.sameMovie(title, year, movie.name, movie.releaseYear) }
                    }
                    val confirmedSeries = emptyList<SeriesItem>()
                    confirmedMovies to confirmedSeries
                }
                TvFlowAiResults(
                    searchedQuery = raw,
                    notice = when {
                        requestedPerson == null -> null
                        verifiedCredits == null -> "Une clé TMDB valide est nécessaire dans les réglages pour vérifier les acteurs. Aucun résultat approximatif n'est affiché."
                        personCheck?.first?.isEmpty() == true -> "Aucun film avec $requestedPerson confirmé dans le catalogue actif."
                        else -> "Distribution vérifiée via TMDB, disponibilité issue du serveur actif."
                    },
                    programs = programs,
                    footballAnswer = footballAnswer,
                    tvQuestion = tvQuestion,
                    movies = if (parsed.seriesOnly && !parsed.filmOnly) emptyList() else (personCheck?.first ?: movies.values)
                        .filter { requestedPerson != null || TvFlowIntent.score(parsed, it.name, it.synopsis, it.releaseYear, movieCategoryNames[it.categoryId], it.tmdbGenreIds) > 0 }
                        .sortedByDescending { TvFlowIntent.score(parsed, it.name, it.synopsis, it.releaseYear, movieCategoryNames[it.categoryId], it.tmdbGenreIds) }
                        .take(80),
                    series = if (parsed.filmOnly && !parsed.seriesOnly) emptyList() else (personCheck?.second ?: series.values)
                        .filter { TvFlowIntent.score(parsed, it.name, it.plot, it.releaseYear, seriesCategoryNames[it.categoryId], it.tmdbGenreIds) > 0 }
                        .sortedByDescending { TvFlowIntent.score(parsed, it.name, it.plot, it.releaseYear, seriesCategoryNames[it.categoryId], it.tmdbGenreIds) }
                        .take(80)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TvFlowAiResults(searchedQuery = raw, error = "Recherche indisponible. Vérifie le catalogue local.")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvFlowAiResults())
}
