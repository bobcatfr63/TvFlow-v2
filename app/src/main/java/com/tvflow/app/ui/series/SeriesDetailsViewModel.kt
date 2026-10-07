package com.tvflow.app.ui.series

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailsUiState(
    val loading: Boolean = true,
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Int? = null,
    val suggestions: List<SeriesItem> = emptyList(),
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

    init { load() }

    fun selectSeason(season: Int) { _state.value = _state.value.copy(selectedSeason = season) }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val episodes = repository.getSeriesEpisodes(seriesId)
                val suggestions = if (categoryId.isBlank()) {
                    emptyList()
                } else {
                    repository.observeSeries(categoryId)
                        .first()
                        .asSequence()
                        .filter { it.seriesId != seriesId }
                        .sortedWith(
                            compareByDescending<SeriesItem> { it.releaseYear ?: 0 }
                                .thenByDescending { it.releaseMonth ?: 0 }
                        )
                        .take(10)
                        .toList()
                }
                episodes to suggestions
            }.onSuccess { (episodes, suggestions) ->
                _state.value = SeriesDetailsUiState(
                    loading = false,
                    episodes = episodes,
                    selectedSeason = episodes.firstOrNull()?.season,
                    suggestions = suggestions
                )
            }.onFailure { error ->
                _state.value = SeriesDetailsUiState(
                    loading = false,
                    error = error.message ?: "Impossible de charger la série"
                )
            }
        }
    }
}
