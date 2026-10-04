package com.bobcatfr63.iptv

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest

enum class MediaSortMode(val label: String) {
    AZ("A-Z"), RATING("★ Note"), RECENT("Récent"), YEAR("Année")
}

fun mediaTimestamp(value: String?): Long {
    val raw = value?.trim()?.toLongOrNull() ?: return 0L
    return if (raw > 10_000_000_000L) raw else raw * 1000L
}

fun mediaYear(title: String): Int {
    val match = Regex("(?:19|20)\\d{2}").findAll(title).lastOrNull()?.value
    return match?.toIntOrNull() ?: 0
}


fun mediaReleaseTimestamp(releaseDate: String?, year: String?, title: String): Long {
    val raw = releaseDate?.trim().orEmpty()
    if (raw.isNotBlank()) {
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy")
        for (pattern in patterns) {
            val parsed = runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).apply { isLenient = false }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        raw.toLongOrNull()?.let { numeric ->
            return if (numeric > 10_000_000_000L) numeric else numeric * 1000L
        }
    }
    val explicitYear = year?.filter { it.isDigit() }?.take(4)?.toIntOrNull()
    val y = explicitYear ?: mediaYear(title)
    if (y > 0) {
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse("$y-01-01")?.time ?: 0L
        }.getOrDefault(0L)
    }
    return 0L
}

fun movieReleaseTimestamp(item: VodStream): Long = mediaReleaseTimestamp(item.releaseDate, item.year, item.name)
fun seriesReleaseTimestamp(item: SeriesItem): Long = mediaReleaseTimestamp(item.releaseDate, item.year, item.name)

fun qualityBadges(title: String): List<String> {
    val n = title.uppercase()
    val out = linkedSetOf<String>()
    when {
        "2160" in n || "4K" in n || "UHD" in n -> out += "4K"
        "1080" in n || "FHD" in n || "FULLHD" in n || "FULL HD" in n -> out += "FHD"
        "720" in n || Regex("(^|[^A-Z])HD([^A-Z]|$)").containsMatchIn(n) -> out += "HD"
    }
    if ("HEVC" in n || "H265" in n || "H.265" in n || "X265" in n) out += "HEVC"
    return out.toList()
}

fun sortMovies(items: List<VodStream>, mode: MediaSortMode): List<VodStream> = when (mode) {
    MediaSortMode.AZ -> items.sortedBy { it.name.lowercase() }
    MediaSortMode.RATING -> items.sortedByDescending { qualityRating(it.rating, it.rating5) }
    MediaSortMode.RECENT -> items.sortedByDescending { mediaTimestamp(it.added) }
    MediaSortMode.YEAR -> items.sortedByDescending { mediaYear(it.name) }
}

fun sortSeries(items: List<SeriesItem>, mode: MediaSortMode): List<SeriesItem> = when (mode) {
    MediaSortMode.AZ -> items.sortedBy { it.name.lowercase() }
    MediaSortMode.RATING -> items.sortedByDescending { qualityRating(it.rating, it.rating5) }
    MediaSortMode.RECENT -> items.sortedByDescending { mediaTimestamp(it.lastModified) }
    MediaSortMode.YEAR -> items.sortedByDescending { mediaYear(it.name) }
}

private fun qualityRating(rating: String?, rating5: Double?): Double {
    val raw = rating?.replace(',', '.')?.toDoubleOrNull()
    return when {
        raw != null && raw > 5.0 -> raw
        raw != null -> raw * 2.0
        rating5 != null -> rating5 * 2.0
        else -> 0.0
    }
}

class CategoryPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("iptv_state", Context.MODE_PRIVATE)
    private fun hiddenKey(tab: HomeTab) = "category_hidden_${tab.name}"
    private fun favoriteKey(tab: HomeTab) = "category_favorite_${tab.name}"

    fun hidden(tab: HomeTab): Set<String> = prefs.getStringSet(hiddenKey(tab), emptySet())?.toSet().orEmpty()
    fun favorites(tab: HomeTab): Set<String> = prefs.getStringSet(favoriteKey(tab), emptySet())?.toSet().orEmpty()
    fun isHidden(tab: HomeTab, id: String) = id in hidden(tab)
    fun isFavorite(tab: HomeTab, id: String) = id in favorites(tab)

    fun toggleHidden(tab: HomeTab, id: String) {
        val values = hidden(tab).toMutableSet()
        if (!values.add(id)) values.remove(id)
        prefs.edit().putStringSet(hiddenKey(tab), values).apply()
    }

    fun toggleFavorite(tab: HomeTab, id: String) {
        val values = favorites(tab).toMutableSet()
        if (!values.add(id)) values.remove(id)
        prefs.edit().putStringSet(favoriteKey(tab), values).apply()
    }

    fun visible(tab: HomeTab, categories: List<Category>): List<Category> {
        val hidden = hidden(tab)
        val favorites = favorites(tab)
        return categories
            .filterNot { it.id in hidden }
            .sortedWith(compareByDescending<Category> { it.id in favorites }.thenBy { it.name.lowercase() })
    }
}

@Composable
fun rememberPersistentGridState(key: String): LazyGridState {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("grid_positions", Context.MODE_PRIVATE) }
    val safe = remember(key) { key.hashCode().toString() }
    val initialIndex = remember(key) { prefs.getInt("${safe}_index", 0) }
    val initialOffset = remember(key) { prefs.getInt("${safe}_offset", 0) }
    val state = remember(key) { LazyGridState(initialIndex, initialOffset) }
    DisposableEffect(state, key) {
        onDispose {
            prefs.edit()
                .putInt("${safe}_index", state.firstVisibleItemIndex)
                .putInt("${safe}_offset", state.firstVisibleItemScrollOffset)
                .apply()
        }
    }
    return state
}

@Composable
fun PosterPrefetch(urls: List<String?>, limit: Int = 36) {
    val context = LocalContext.current
    val targets = remember(urls, limit) { urls.asSequence().filterNotNull().filter { it.isNotBlank() }.distinct().take(limit).toList() }
    LaunchedEffect(targets) {
        targets.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .build()
            )
        }
    }
}

@Composable
fun QualityBadges(title: String, modifier: Modifier = Modifier) {
    val badges = remember(title) { qualityBadges(title) }
    if (badges.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        badges.forEach { badge ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(5.dp)
            ) {
                Text(badge, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CategoryManagerDialog(
    tab: HomeTab,
    categories: List<Category>,
    preferences: CategoryPreferences,
    revision: Int,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val ignoredRevision = revision
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gérer les catégories") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text("★ place une catégorie en tête. Masquer la retire de la navigation.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                items(categories.size, key = { categories[it].id }) { index ->
                    val category = categories[index]
                    val favorite = preferences.isFavorite(tab, category.id)
                    val hidden = preferences.isHidden(tab, category.id)
                    Row(
                        Modifier.fillMaxWidth().animateContentSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { preferences.toggleFavorite(tab, category.id); onChanged() }) {
                            Text(if (favorite) "★" else "☆")
                        }
                        TextButton(onClick = { preferences.toggleHidden(tab, category.id); onChanged() }) {
                            Text(if (hidden) "Afficher" else "Masquer")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

@Composable
fun NewContentScreen(
    movies: List<VodStream>,
    series: List<SeriesItem>,
    loading: Boolean,
    onMovie: (VodStream) -> Unit,
    onSeries: (SeriesItem) -> Unit
) {
    PosterPrefetch(movies.map { it.icon } + series.map { it.cover }, 48)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Nouveautés", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Dernières sorties du catalogue, classées par date de sortie.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loading) item {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
        if (movies.isNotEmpty()) {
            item { Text("Films récents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.primary) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(movies.size, key = { movies[it].id }) { index ->
                        val movie = movies[index]
                        NewPosterCard(movie.name, movie.icon, movie.releaseDate ?: movie.year) { onMovie(movie) }
                    }
                }
            }
        }
        if (series.isNotEmpty()) {
            item { Text("Séries récentes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.primary) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(series.size, key = { series[it].id }) { index ->
                        val show = series[index]
                        NewPosterCard(show.name, show.cover, show.releaseDate ?: show.year) { onSeries(show) }
                    }
                }
            }
        }
        if (!loading && movies.isEmpty() && series.isEmpty()) {
            item { Text("Aucune nouveauté disponible pour le moment.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NewPosterCard(title: String, image: String?, timestamp: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(155.dp).animateContentSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Box {
            AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
            QualityBadges(title, Modifier.align(Alignment.BottomStart).padding(5.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (timestamp != null) Text(timestamp, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
    }
}
