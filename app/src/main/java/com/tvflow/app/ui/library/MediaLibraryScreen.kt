package com.tvflow.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem

@Composable
fun MediaLibraryScreen(
    onVodSelected: (VodItem) -> Unit,
    onSeriesSelected: (SeriesItem) -> Unit,
    viewModel: MediaLibraryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val availableSections by viewModel.availableSections.collectAsState()
    val selectedSection by viewModel.selectedSection.collectAsState()
    val selectedId by viewModel.selectedCategoryId.collectAsState()
    val releaseYears by viewModel.releaseYears.collectAsState()
    val releaseMonths by viewModel.releaseMonths.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val vod = viewModel.vod.collectAsLazyPagingItems()
    val series = viewModel.series.collectAsLazyPagingItems()

    LaunchedEffect(categories, selectedId) {
        if (selectedId == null && categories.isNotEmpty()) viewModel.selectCategory(categories.first().id)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A3554), Color(0xFF06111B), Color(0xFF03080D))
                )
            )
            .padding(top = 24.dp)
    ) {
        Text(
            if (viewModel.type == MediaType.VOD) "Films" else "Séries",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (availableSections.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableSections, key = { it.name }) { section ->
                        ReleaseFilterChip(
                            label = mediaSectionLabel(section),
                            selected = selectedSection == section,
                            onClick = { viewModel.selectSection(section) }
                        )
                    }
                }
            }
            Text(
                "Sorties récentes • date de sortie réelle",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ReleaseFilterChip(
                        label = "Toutes les dates",
                        selected = selectedYear == null,
                        onClick = { viewModel.selectYear(null) }
                    )
                }
                items(releaseYears, key = { it }) { year ->
                    ReleaseFilterChip(
                        label = year.toString(),
                        selected = selectedYear == year,
                        onClick = { viewModel.selectYear(year) }
                    )
                }
            }
            if (releaseYears.isEmpty()) {
                Text(
                    "Dates en cours d’enrichissement. Xtream est utilisé d’abord, puis TMDb complète les informations manquantes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedYear != null && releaseMonths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ReleaseFilterChip(
                            label = "Tous les mois",
                            selected = selectedMonth == null,
                            onClick = { viewModel.selectMonth(null) }
                        )
                    }
                    items(releaseMonths, key = { it }) { month ->
                        ReleaseFilterChip(
                            label = monthName(month),
                            selected = selectedMonth == month,
                            onClick = { viewModel.selectMonth(month) }
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxSize().padding(top = 10.dp)) {
            Column(
                modifier = Modifier.width(280.dp).fillMaxHeight().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        val selected = category.id == selectedId
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Card(
                            onClick = { viewModel.selectCategory(category.id) },
                            interactionSource = interaction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = if (focused) 1.04f else 1f
                                    scaleY = if (focused) 1.04f else 1f
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
                                if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Text(
                                text = category.name,
                                color = when {
                                    focused -> MaterialTheme.colorScheme.onPrimaryContainer
                                    selected -> MaterialTheme.colorScheme.onPrimary
                                    else -> Color.White
                                },
                                modifier = Modifier.padding(14.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.type == MediaType.VOD) {
                    items(count = vod.itemCount) { index ->
                        vod[index]?.let { item ->
                            MediaPoster(
                                name = item.name,
                                image = item.posterUrl,
                                rating = item.rating,
                                releaseYear = item.releaseYear,
                                releaseMonth = item.releaseMonth,
                                synopsis = item.synopsis
                            ) { onVodSelected(item) }
                        }
                    }
                } else {
                    items(count = series.itemCount) { index ->
                        series[index]?.let { item ->
                            MediaPoster(
                                name = item.name,
                                image = item.coverUrl,
                                rating = item.rating,
                                releaseYear = item.releaseYear,
                                releaseMonth = item.releaseMonth,
                                synopsis = item.plot
                            ) { onSeriesSelected(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPoster(
    name: String,
    image: String?,
    rating: String?,
    releaseYear: Int?,
    releaseMonth: Int?,
    synopsis: String?,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else Color(0xFF0B2033)
        ),
        border = BorderStroke(
            if (focused) 4.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.10f)
        )
    ) {
        Column {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(164.dp)
            )
            Text(
                name,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (releaseYear != null) {
                val dateLabel = if (releaseMonth != null) {
                    monthName(releaseMonth) + " " + releaseYear
                } else {
                    releaseYear.toString()
                }
                Text(
                    dateLabel,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (!rating.isNullOrBlank()) {
                Text(
                    "★ $rating",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!synopsis.isNullOrBlank()) {
                Text(
                    synopsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (focused) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReleaseFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        colors = CardDefaults.cardColors(
            containerColor = when {
                focused -> MaterialTheme.colorScheme.primaryContainer
                selected -> MaterialTheme.colorScheme.primary
                else -> Color(0xFF0B2033)
            }
        ),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = when {
                focused -> MaterialTheme.colorScheme.onPrimaryContainer
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> Color.White
            }
        )
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


private fun mediaSectionLabel(section: MediaSection): String = when (section) {
    MediaSection.FRANCE -> "France"
    MediaSection.KIDS -> "Enfants"
    MediaSection.OTHER -> "Autres"
}
