package com.tvflow.app.ui.series

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.Episode
import com.tvflow.app.domain.SeriesItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailsUiState(
    val loading: Boolean = true,
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Int? = null,
    val suggestions: List<SeriesItem> = emptyList(),
    val watchedEpisodeIds: Set<String> = emptySet(),
    val error: String? = null
)

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    private val repository: XtreamRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val seriesId: Int = checkNotNull(savedStateHandle["seriesId"])
    private val categoryId: String = savedStateHandle.get<String>("categoryId").orEmpty()
    private val _state = MutableStateFlow(SeriesDetailsUiState())
    val state: StateFlow<SeriesDetailsUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = repository.observeMediaFavoriteIds("series")
        .map { seriesId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        viewModelScope.launch { repository.toggleMediaFavorite("series", seriesId) }
    }

    init {
        load()
        viewModelScope.launch {
            repository.observeCompletedEpisodeIds(seriesId).collect { watched ->
                _state.value = _state.value.copy(watchedEpisodeIds = watched)
            }
        }
    }

    fun selectSeason(season: Int) { _state.value = _state.value.copy(selectedSeason = season) }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val episodes = repository.getSeriesEpisodes(seriesId)
                val suggestions = runCatching {
                    repository.getSeriesSuggestions(categoryId, seriesId)
                }.getOrElse { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    emptyList()
                }
                episodes to suggestions
            }.onSuccess { (episodes, suggestions) ->
                _state.value = _state.value.copy(
                    loading = false,
                    episodes = episodes,
                    selectedSeason = episodes.firstOrNull()?.season,
                    suggestions = suggestions,
                    error = null
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                // Never log Xtream URLs or exception messages: they can contain credentials.
                Log.w("TVFlowSeries", "Episode loading failed: category=${safeSeriesFailureCategory(error)}, seriesId=$seriesId")
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Impossible de charger les épisodes. Vérifiez la connexion au serveur IPTV, puis réessayez."
                )
            }
        }
    }
}
