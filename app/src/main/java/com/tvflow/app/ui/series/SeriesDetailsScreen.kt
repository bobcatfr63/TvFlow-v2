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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.tvflow.app.domain.Episode
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.util.isTelevisionUi

@Composable
fun SeriesDetailsScreen(
    seriesName: String,
    coverUrl: String?,
    plot: String?,
    rating: String?,
    releaseYear: Int?,
    releaseMonth: Int?,
    onPlayEpisode: (Episode) -> Unit,
    onSuggestion: (SeriesItem) -> Unit = {},
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isTv = androidx.compose.ui.platform.LocalContext.current.isTelevisionUi()
    val seasons = state.episodes.map { it.season }.distinct()
    val episodes = state.episodes.filter {
        state.selectedSeason == null || it.season == state.selectedSeason
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A3554), Color(0xFF06111B), Color(0xFF03080D))
                )
            )
            .padding(if (isTv) 32.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                coverUrl?.takeIf { it.isNotBlank() }?.let { image ->
                    AsyncImage(
                        model = image,
                        contentDescription = seriesName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(210.dp).height(315.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("TVFlow CINEMA  •  SÉRIE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(
                        seriesName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    val dateLabel = when {
                        releaseYear != null && releaseMonth != null ->
                            monthName(releaseMonth) + " " + releaseYear
                        releaseYear != null -> releaseYear.toString()
                        else -> null
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        dateLabel?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        rating?.takeIf { it.isNotBlank() }?.let {
                            Text("★ $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        plot?.takeIf { it.isNotBlank() } ?: "Synopsis indisponible.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (state.loading) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        } else if (state.error != null) {
            item {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Réessayer")
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                if (focused) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.14f)
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
            }

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
                        containerColor = if (focused) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color(0xFF0B2033)
                        }
                    ),
                    border = BorderStroke(
                        if (focused) 4.dp else 1.dp,
                        if (focused) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.10f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        episode.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(132.dp)
                                    .height(76.dp)
                                    .padding(end = 12.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    if (episode.id in state.watchedEpisodeIds) append("✓ Vu  •  ")
                                    append("S")
                                    append(episode.season)
                                    append(" • E")
                                    append(episode.episodeNumber)
                                    append("  ")
                                    append(episode.title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (episode.id in state.watchedEpisodeIds) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
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

            if (state.suggestions.isNotEmpty()) {
                item {
                    Text(
                        "Suggestions",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.suggestions, key = { it.seriesId }) { item ->
                            SeriesSuggestionCard(item = item, onClick = { onSuggestion(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesSuggestionCard(
    item: SeriesItem,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
            },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer
            else Color(0xFF0B2033)
        ),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.10f)
        )
    ) {
        Column {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp)
            )
            Text(
                item.name,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Janvier"
    2 -> "Février"
    3 -> "Mars"
    4 -> "Avril"
    5 -> "Mai"
    6 -> "Juin"
    7 -> "Juillet"
    8 -> "Août"
    9 -> "Septembre"
    10 -> "Octobre"
    11 -> "Novembre"
    12 -> "Décembre"
    else -> month.toString()
}
