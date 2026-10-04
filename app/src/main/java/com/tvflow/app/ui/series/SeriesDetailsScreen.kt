package com.tvflow.app.ui.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.tvflow.app.domain.Episode

@Composable
fun SeriesDetailsScreen(
    seriesName: String,
    onPlayEpisode: (Episode) -> Unit,
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val seasons = state.episodes.map { it.season }.distinct()
    val episodes = state.episodes.filter { state.selectedSeason == null || it.season == state.selectedSeason }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A3554), Color(0xFF06111B), Color(0xFF03080D))
                )
            )
            .padding(32.dp)
    ) {
        Text(
            seriesName,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            return@Column
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) { Text("Réessayer") }
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 18.dp)) {
            items(seasons) { season ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val selected = state.selectedSeason == season
                Card(
                    onClick = { viewModel.selectSeason(season) },
                    interactionSource = interaction,
                    modifier = Modifier.graphicsLayer {
                        scaleX = if (focused) 1.06f else 1f
                        scaleY = if (focused) 1.06f else 1f
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            focused -> MaterialTheme.colorScheme.primaryContainer
                            selected -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFF0B2033)
                        }
                    ),
                    border = BorderStroke(
                        if (focused) 4.dp else 1.dp,
                        if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f)
                    )
                ) {
                    Text(
                        text = "Saison $season",
                        color = when {
                            focused -> MaterialTheme.colorScheme.onPrimaryContainer
                            selected -> MaterialTheme.colorScheme.onPrimary
                            else -> Color.White
                        },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(episodes, key = { it.id }) { episode ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                Card(
                    onClick = { onPlayEpisode(episode) },
                    interactionSource = interaction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = if (focused) 1.015f else 1f
                            scaleY = if (focused) 1.015f else 1f
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else Color(0xFF0B2033)
                    ),
                    border = BorderStroke(
                        if (focused) 4.dp else 1.dp,
                        if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.10f)
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        episode.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(132.dp).height(76.dp).padding(end = 12.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "S${episode.season} • E${episode.episodeNumber}  ${episode.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            episode.plot?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text("▶", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
