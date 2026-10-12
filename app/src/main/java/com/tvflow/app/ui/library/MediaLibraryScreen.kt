package com.tvflow.app.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
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
    val selectedId by viewModel.selectedCategoryId.collectAsState()
    val releaseYears by viewModel.releaseYears.collectAsState()
    val releaseMonths by viewModel.releaseMonths.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val vod = viewModel.vod.collectAsLazyPagingItems()
    val series = viewModel.series.collectAsLazyPagingItems()
    val query by viewModel.searchQuery.collectAsState()
    val searchedVod by viewModel.searchedVod.collectAsState()
    val searchedSeries by viewModel.searchedSeries.collectAsState()
    var searchMode by remember(viewModel.type) { mutableStateOf(false) }
    var expandedDateFilters by remember(viewModel.type) { mutableStateOf(false) }
    BackHandler(enabled = searchMode) {
        searchMode = false
        viewModel.searchQuery.value = ""
    }
    val context = LocalContext.current
    val isTv = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    val compactMobile = !isTv
    var preview by remember(viewModel.type) { mutableStateOf<MediaPreview?>(null) }

    val screenHorizontalPadding = if (compactMobile) 12.dp else 24.dp
    val topPadding = if (compactMobile) 6.dp else 24.dp
    val titleVerticalPadding = if (compactMobile) 4.dp else 12.dp
    val filterSpacing = if (compactMobile) 4.dp else 8.dp
    val contentTopPadding = if (compactMobile) 4.dp else 10.dp
    val categoryWidth = if (compactMobile) 154.dp else 280.dp

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
            .padding(top = topPadding)
    ) {
        Text(
            if (viewModel.type == MediaType.VOD) "Films" else "Séries",
            style = if (compactMobile) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                horizontal = screenHorizontalPadding,
                vertical = titleVerticalPadding
            )
        )
        ReleaseFilterChip(
            label = if (searchMode) "← Retour aux catégories" else "⌕ Rechercher dans les ${if (viewModel.type == MediaType.VOD) "films" else "séries"}",
            selected = searchMode,
            onClick = { searchMode = !searchMode; viewModel.searchQuery.value = "" },
            compact = compactMobile
        )
        if (searchMode) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                label = { Text("Rechercher ${if (viewModel.type == MediaType.VOD) "un film" else "une série"}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = screenHorizontalPadding)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (compactMobile) 104.dp else 160.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.type == MediaType.VOD) {
                    items(searchedVod.size) { index ->
                        val item = searchedVod[index]
                        MediaPoster(item.name, item.posterUrl, item.rating, item.releaseYear, item.releaseMonth,
                            item.synopsis, compactMobile) { onVodSelected(item) }
                    }
                } else {
                    items(searchedSeries.size) { index ->
                        val item = searchedSeries[index]
                        MediaPoster(item.name, item.coverUrl, item.rating, item.releaseYear, item.releaseMonth,
                            item.plot, compactMobile) { onSeriesSelected(item) }
                    }
                }
            }
        } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(filterSpacing)
        ) {
            ReleaseFilterChip(
                label = when {
                    expandedDateFilters -> "▴ Masquer les filtres de dates"
                    selectedYear != null -> "▾ Sorties : ${selectedYear}${selectedMonth?.let { " • ${monthName(it)}" }.orEmpty()}"
                    else -> "▾ Filtrer par date de sortie"
                },
                selected = selectedYear != null,
                onClick = { expandedDateFilters = !expandedDateFilters },
                compact = compactMobile
            )
            if (expandedDateFilters) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(filterSpacing)) {
                item {
                    ReleaseFilterChip(
                        label = "Toutes les dates",
                        selected = selectedYear == null,
                        onClick = { viewModel.selectYear(null) },
                        compact = compactMobile
                    )
                }
                items(releaseYears, key = { it }) { year ->
                    ReleaseFilterChip(
                        label = year.toString(),
                        selected = selectedYear == year,
                        onClick = { viewModel.selectYear(year) },
                        compact = compactMobile
                    )
                }
            }
            if (releaseYears.isEmpty()) {
                Text(
                    "Les dates de sortie s’afficheront dès qu’elles seront disponibles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedYear != null && releaseMonths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(filterSpacing)) {
                    item {
                        ReleaseFilterChip(
                            label = "Tous les mois",
                            selected = selectedMonth == null,
                            onClick = { viewModel.selectMonth(null) },
                            compact = compactMobile
                        )
                    }
                    items(releaseMonths, key = { it }) { month ->
                        ReleaseFilterChip(
                            label = monthName(month),
                            selected = selectedMonth == month,
                            onClick = { viewModel.selectMonth(month) },
                            compact = compactMobile
                        )
                    }
                }
            }
            }
        }
        Row(Modifier.fillMaxSize().padding(top = contentTopPadding)) {
            Column(
                modifier = Modifier
                    .width(categoryWidth)
                    .fillMaxHeight()
                    .padding(horizontal = if (compactMobile) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactMobile) 4.dp else 8.dp)
            ) {
                if (isTv) {
                    MediaPreviewPanel(
                        preview = preview,
                        isSeries = viewModel.type == MediaType.SERIES,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compactMobile) 4.dp else 8.dp)
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
                                    scaleX = 1f
                                    scaleY = 1f
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
                                modifier = Modifier.padding(
                                    horizontal = if (compactMobile) 10.dp else 14.dp,
                                    vertical = if (compactMobile) 8.dp else 14.dp
                                ),
                                maxLines = 1,
                                style = if (compactMobile) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (compactMobile) 104.dp else 160.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (compactMobile) 4.dp else 8.dp,
                        end = if (compactMobile) 8.dp else 16.dp,
                        bottom = if (compactMobile) 8.dp else 16.dp
                    ),
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
                                synopsis = item.synopsis,
                                compact = compactMobile,
                                onFocused = {
                                    preview = MediaPreview(
                                        title = item.name,
                                        image = item.posterUrl,
                                        rating = item.rating,
                                        releaseYear = item.releaseYear,
                                        releaseMonth = item.releaseMonth,
                                        synopsis = item.synopsis
                                    )
                                }
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
                                synopsis = item.plot,
                                compact = compactMobile,
                                onFocused = {
                                    preview = MediaPreview(
                                        title = item.name,
                                        image = item.coverUrl,
                                        rating = item.rating,
                                        releaseYear = item.releaseYear,
                                        releaseMonth = item.releaseMonth,
                                        synopsis = item.plot
                                    )
                                }
                            ) { onSeriesSelected(item) }
                        }
                    }
                }
            }
        }
        }
    }
}

private data class MediaPreview(
    val title: String,
    val image: String?,
    val rating: String?,
    val releaseYear: Int?,
    val releaseMonth: Int?,
    val synopsis: String?
)

@Composable
private fun MediaPreviewPanel(
    preview: MediaPreview?,
    isSeries: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(if (preview == null) 76.dp else 150.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2033)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        if (preview == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Aperçu",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (isSeries) "Choisis une série" else "Choisis un film",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = preview.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(76.dp).fillMaxHeight()
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        preview.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val dateLabel = when {
                        preview.releaseYear != null && preview.releaseMonth != null ->
                            monthName(preview.releaseMonth) + " " + preview.releaseYear
                        preview.releaseYear != null -> preview.releaseYear.toString()
                        else -> null
                    }
                    val meta = listOfNotNull(
                        dateLabel,
                        preview.rating?.takeIf { it.isNotBlank() }?.let { "★ $it" }
                    ).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        preview.synopsis?.takeIf { it.isNotBlank() }
                            ?: "Synopsis en cours de récupération…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
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
    compact: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = 1f
                scaleY = 1f
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
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 160.dp else 184.dp)
            )
            Text(
                name.replace(Regex("""(?i)^\s*(?:FR|VF|VOSTFR)\s*[-:|]\s*"""), ""),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
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
            if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                Text(
                    "★ $rating",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (compact && !synopsis.isNullOrBlank()) {
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
    onClick: () -> Unit,
    compact: Boolean = false
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
            modifier = Modifier.padding(
                horizontal = if (compact) 11.dp else 14.dp,
                vertical = if (compact) 6.dp else 9.dp
            ),
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
