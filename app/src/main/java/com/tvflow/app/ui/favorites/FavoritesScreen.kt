package com.tvflow.app.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.VodItem
import com.tvflow.app.domain.SeriesItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FavoritesViewModel @Inject constructor(private val repository: XtreamRepository) : ViewModel() {
    val channels = repository.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val films = repository.observeFavoriteVodItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val series = repository.observeFavoriteSeriesItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun removeChannel(id: Int) = viewModelScope.launch { repository.toggleFavorite(id) }
    fun removeFilm(id: Int) = viewModelScope.launch { repository.toggleMediaFavorite("vod", id) }
    fun removeSeries(id: Int) = viewModelScope.launch { repository.toggleMediaFavorite("series", id) }
}

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onChannel: (LiveChannel) -> Unit,
    onVod: (VodItem) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val films by viewModel.films.collectAsState()
    val series by viewModel.series.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("← Retour") }
            Text("★ Mes favoris", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Chaînes", "Films", "Séries").forEachIndexed { index, label ->
                if (index == tab) Button(onClick = { tab = index }) { Text(label) }
                else OutlinedButton(onClick = { tab = index }) { Text(label) }
            }
        }
        val count = when (tab) { 0 -> channels.size; 1 -> films.size; else -> series.size }
        if (count == 0) Text("Aucun favori dans cette rubrique.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> items(channels, key = { it.streamId }) { item ->
                    FavoriteRow(item.name, { onChannel(item) }, { viewModel.removeChannel(item.streamId) })
                }
                1 -> items(films, key = { it.streamId }) { item ->
                    FavoriteRow(item.name, { onVod(item) }, { viewModel.removeFilm(item.streamId) })
                }
                else -> items(series, key = { it.seriesId }) { item ->
                    FavoriteRow(item.name, { onSeries(item) }, { viewModel.removeSeries(item.seriesId) })
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(title: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(onClick = onOpen, modifier = Modifier.weight(1f)) {
            Text(title, modifier = Modifier.padding(18.dp), maxLines = 2)
        }
        OutlinedButton(onClick = onRemove) { Text("★ Retirer") }
    }
}
