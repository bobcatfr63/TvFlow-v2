package com.tvflow.app.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.VodItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VodDetailsViewModel @Inject constructor(
    repository: XtreamRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val streamId: Int = checkNotNull(savedStateHandle["streamId"])
    private val categoryId: String = savedStateHandle.get<String>("categoryId").orEmpty()

    val suggestions: StateFlow<List<VodItem>> =
        if (categoryId.isBlank()) {
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        } else {
            repository.observeVod(categoryId)
                .map { items ->
                    items.asSequence()
                        .filter { it.streamId != streamId }
                        .sortedWith(
                            compareByDescending<VodItem> { it.releaseYear ?: 0 }
                                .thenByDescending { it.releaseMonth ?: 0 }
                                .thenByDescending { it.added ?: 0L }
                        )
                        .take(10)
                        .toList()
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
}
