package com.tvflow.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
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
    val sortOrder by viewModel.sortOrder.collectAsState()
    val releaseYears by viewModel.releaseYears.collectAsState()
    val releaseMonths by viewModel.releaseMonths.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val vod = viewModel.vod.collectAsLazyPagingItems()
    val series = viewModel.series.collectAsLazyPagingItems()
    val query by viewModel.searchQuery.collectAsState()
    val searchedVod by viewModel.searchedVod.collectAsState()
    val searchedSeries by viewModel.searchedSeries.collectAsState()
    var searchMode by rememberSaveable(viewModel.type) { mutableStateOf(false) }
    var expandedDateFilters by rememberSaveable(viewModel.type) { mutableStateOf(false) }
    BackHandler(enabled = searchMode) {
        searchMode = false
        viewModel.searchQuery.value = ""
    }
    val context = LocalContext.current
    val isTv = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    var selectedPreview by remember(viewModel.type) { mutableStateOf<MediaPreview?>(null) }
    var trailerKey by remember { mutableStateOf<String?>(null) }
    val trailerCache = remember { LinkedHashMap<String, String?>() }
    LaunchedEffect(selectedPreview?.title, selectedPreview?.releaseYear, searchMode, isTv) {
        trailerKey = null
        val item = selectedPreview ?: return@LaunchedEffect
        if (!isTv || searchMode) return@LaunchedEffect
        delay(2_000L)
        val cacheKey = "${viewModel.type}:${item.title}:${item.releaseYear}"
        trailerKey = if (trailerCache.containsKey(cacheKey)) trailerCache[cacheKey] else {
            val result = runCatching { viewModel.resolveTrailer(item.title, item.releaseYear) }.getOrNull()
            if (trailerCache.size >= 40) trailerCache.remove(trailerCache.keys.first())
            trailerCache[cacheKey] = result
            result
        }
    }
    val compactMobile = !isTv
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val reduceMotion = powerManager?.isPowerSaveMode == true

    val screenHorizontalPadding = if (compactMobile) 12.dp else 24.dp
    val topPadding = if (compactMobile) 6.dp else 24.dp
    val titleVerticalPadding = if (compactMobile) 4.dp else 12.dp
    val filterSpacing = if (compactMobile) 4.dp else 8.dp
    val contentTopPadding = if (compactMobile) 4.dp else 10.dp
    val categoryWidth = if (compactMobile) 154.dp else 280.dp
    // Save scroll positions while switching between the library and detail screens.
    val categoryListState = rememberLazyListState()
    val mediaGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()
    val gridMoving = mediaGridState.isScrollInProgress
    val searchMoving = searchGridState.isScrollInProgress

    // A category has its own first page. Never keep the previous category's scroll offset.
    LaunchedEffect(selectedId) {
        selectedPreview = null
        if (selectedId != null) mediaGridState.scrollToItem(0)
    }

    // Prefetch only a few already-loaded posters ahead of the visible row.
    // peek() does not trigger additional Paging loads or network catalogue requests.
    LaunchedEffect(selectedId, viewModel.type, mediaGridState, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        val recentlyRequested = LinkedHashSet<String>()
        snapshotFlow {
            if (mediaGridState.isScrollInProgress) -1
            else mediaGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collectLatest { lastVisible ->
                if (lastVisible < 0) return@collectLatest
                // Wait until the grid settles instead of competing with fast DPAD input.
                delay(120)
                val count = if (viewModel.type == MediaType.VOD) vod.itemCount else series.itemCount
                // Two ahead is enough on low-memory TV devices; avoid competing with rendering.
                for (index in (lastVisible + 1) until minOf(lastVisible + 3, count)) {
                    val url = if (viewModel.type == MediaType.VOD) vod.peek(index)?.posterUrl
                        else series.peek(index)?.coverUrl
                    if (!url.isNullOrBlank() && recentlyRequested.add(url)) {
                        // Keep a small per-category request history, not an unbounded URL set.
                        if (recentlyRequested.size > 80) recentlyRequested.remove(recentlyRequested.first())
                        loader.enqueue(ImageRequest.Builder(context).data(url).size(240, 360).build())
                    }
                }
            }
    }

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
        if (!compactMobile && selectedPreview != null) {
            MediaPreviewPanel(
                preview = selectedPreview,
                trailerKey = trailerKey,
                isSeries = viewModel.type == MediaType.SERIES,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
        }
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
                columns = GridCells.Fixed(if (compactMobile) 3 else 5),
                state = searchGridState,
                modifier = Modifier.fillMaxSize().padding(horizontal = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.type == MediaType.VOD) {
                    items(searchedVod.size, key = { searchedVod[it].streamId }) { index ->
                        val item = searchedVod[index]
                        MediaPoster(item.name, item.posterUrl, item.rating, item.releaseYear, item.releaseMonth,
                            item.synopsis, compactMobile, reduceMotion || searchMoving,
                            onFocused = { selectedPreview = MediaPreview(item.name, item.posterUrl, item.rating, item.releaseYear, item.releaseMonth, item.synopsis) }
                        ) { onVodSelected(item) }
                    }
                } else {
                    items(searchedSeries.size, key = { searchedSeries[it].seriesId }) { index ->
                        val item = searchedSeries[index]
                        MediaPoster(item.name, item.coverUrl, item.rating, item.releaseYear, item.releaseMonth,
                            item.plot, compactMobile, reduceMotion || searchMoving,
                            onFocused = { selectedPreview = MediaPreview(item.name, item.coverUrl, item.rating, item.releaseYear, item.releaseMonth, item.plot) }
                        ) { onSeriesSelected(item) }
                    }
                }
            }
        } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(filterSpacing)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(filterSpacing)) {
                ReleaseFilterChip(
                    label = "Sortie réelle : récent → ancien",
                    selected = sortOrder == "DATE",
                    onClick = { viewModel.selectSort("DATE") },
                    compact = compactMobile
                )
                ReleaseFilterChip(
                    label = "Titre : A → Z",
                    selected = sortOrder == "AZ",
                    onClick = { viewModel.selectSort("AZ") },
                    compact = compactMobile
                )
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
                LazyColumn(
                    state = categoryListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compactMobile) 4.dp else 8.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        val selected = category.id == selectedId && selectedYear == null
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        val focusScale by animateFloatAsState(
                            targetValue = if (focused) 1.025f else 1f,
                            animationSpec = tween(durationMillis = if (reduceMotion) 0 else 90),
                            label = "smart-motion-category"
                        )
                        Card(
                            onClick = { viewModel.selectCategory(category.id) },
                            interactionSource = interaction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = focusScale
                                    scaleY = focusScale
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
                columns = GridCells.Fixed(if (compactMobile) 3 else 5),
                state = mediaGridState,
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
                if (categories.isEmpty() && vod.itemCount == 0 && series.itemCount == 0) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Catalogue en attente", style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text("Les ${if (viewModel.type == MediaType.VOD) "films" else "séries"} de ta source IPTV apparaîtront ici après synchronisation.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (viewModel.type == MediaType.VOD) {
                    items(count = vod.itemCount, key = vod.itemKey { it.streamId }) { index ->
                        vod[index]?.let { item ->
                            MediaPoster(
                                name = item.name,
                                reduceMotion = reduceMotion || gridMoving,
                                image = item.posterUrl,
                                rating = item.rating,
                                releaseYear = item.releaseYear,
                                releaseMonth = item.releaseMonth,
                                synopsis = item.synopsis,
                                compact = compactMobile,
                                onFocused = { selectedPreview = MediaPreview(item.name, item.posterUrl, item.rating, item.releaseYear, item.releaseMonth, item.synopsis) }
                            ) { onVodSelected(item) }
                        }
                    }
                } else {
                    items(count = series.itemCount, key = series.itemKey { it.seriesId }) { index ->
                        series[index]?.let { item ->
                            MediaPoster(
                                name = item.name,
                                reduceMotion = reduceMotion || gridMoving,
                                image = item.coverUrl,
                                rating = item.rating,
                                releaseYear = item.releaseYear,
                                releaseMonth = item.releaseMonth,
                                synopsis = item.plot,
                                compact = compactMobile,
                                onFocused = { selectedPreview = MediaPreview(item.name, item.coverUrl, item.rating, item.releaseYear, item.releaseMonth, item.plot) }
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
    trailerKey: String?,
    isSeries: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.height(if (preview == null) 76.dp else if (trailerKey != null) 225.dp else 190.dp),
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
                if (trailerKey != null) {
                    TrailerWebPreview(trailerKey, Modifier.width(290.dp).fillMaxHeight())
                } else AsyncImage(
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
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = {
                        val query = Uri.encode(preview.title + " bande annonce officielle " + (preview.releaseYear ?: ""))
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
                        runCatching { context.startActivity(intent) }
                    }) { Text("▶ Chercher une bande-annonce", color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun TrailerWebPreview(videoKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val webView = remember(videoKey) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)
            val url = "https://www.youtube-nocookie.com/embed/$videoKey?autoplay=1&mute=0&playsinline=1&controls=1&rel=0"
            val html = """
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/></head>
                <body style="margin:0;background:black;overflow:hidden">
                <iframe width="100%" height="100%" src="$url"
                    allow="autoplay; encrypted-media; picture-in-picture"
                    referrerpolicy="strict-origin-when-cross-origin"
                    style="border:0" allowfullscreen></iframe>
                </body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private val mediaTitlePrefix = Regex("""(?i)^\s*(?:FR|VF|VOSTFR)\s*[-:|]\s*""")

@Composable
private fun MediaPoster(
    name: String,
    image: String?,
    rating: String?,
    releaseYear: Int?,
    releaseMonth: Int?,
    synopsis: String?,
    compact: Boolean = false,
    reduceMotion: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 100),
        label = "smart-motion-poster"
    )
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
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
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 142.dp else 126.dp)
            )
            Text(
                name.replace(mediaTitlePrefix, ""),
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
            if (false && compact && !synopsis.isNullOrBlank()) {
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
