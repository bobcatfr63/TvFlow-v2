package com.tvflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tvflow.app.domain.VodItem
import com.tvflow.app.domain.SeriesItem
import androidx.compose.foundation.verticalScroll

@Composable
internal fun NextMobileDashboard(
    state: DashboardUiState,
    onLive: () -> Unit,
    onGuide: () -> Unit,
    onVod: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onFavorites: () -> Unit,
    onVodItem: (VodItem) -> Unit,
    onSeriesItem: (SeriesItem) -> Unit
) {
    val featuredSeries = (state.recommendedSeries + state.recentSeriesReleases)
        .distinctBy { it.seriesId }
        .firstOrNull { it.coverUrl?.startsWith("http") == true && isGoodFeaturedTitle(it.name) }
    val featuredFilm = (state.recommendedVod + state.recentVodReleases)
        .distinctBy { it.streamId }
        .firstOrNull { it.posterUrl?.startsWith("http") == true && isGoodFeaturedTitle(it.name) }
    val series = featuredSeries
    val film = if (series == null) featuredFilm else null
    val title = cleanNextDisplayTitle(series?.name ?: film?.name ?: "Bienvenue sur TVFlow")
    val image = series?.coverUrl ?: film?.posterUrl
    val hasFeaturedMedia = series != null || film != null
    val white = Color(0xFFF3F6FF)
    val blue = Color(0xFF078AFF)
    Box(Modifier.fillMaxSize().background(Color(0xFF040910))) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TVFlow", color = white, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("TVFlow-v2 Next", color = blue, fontSize = 12.sp)
            }
            Text("⌕", Modifier.clickable(onClick = onSearch).padding(10.dp), color = white, fontSize = 24.sp)
            Text("⚙", Modifier.clickable(onClick = onSettings).padding(10.dp), color = white, fontSize = 23.sp)
        }
Box(Modifier.fillMaxWidth().height(278.dp)) {
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.88f).align(Alignment.CenterEnd))
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(Color(0xF9040910), Color(0xB8040910), Color(0x22040910)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x70040910), Color(0xFF040910)))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(if (!hasFeaturedMedia) "DÉCOUVRIR TVFLOW" else if (series != null) "SÉRIE À LA UNE" else "FILM À LA UNE", color = blue,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(title, color = white, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(series?.releaseYear ?: film?.releaseYear).joinToString() +
                    if (series != null) "  •  Série  •  HD" else "  •  Film  •  HD",
                    color = Color(0xFF43D5B1), fontSize = 11.sp)
                Text((series?.plot ?: film?.synopsis).orEmpty().takeIf { isReadableNextSynopsis(it) }
                    ?: "Découvrez ce programme dans votre catalogue TVFlow.", color = white.copy(alpha = 0.78f),
                    fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.85f))
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasFeaturedMedia) "▶  Voir maintenant" else "▶  Explorer les films", Modifier.clip(RoundedCornerShape(10.dp)).background(blue)
                        .clickable { if (series != null) onSeriesItem(series) else if (film != null) onVodItem(film) else onVod() }
                        .padding(horizontal = 14.dp, vertical = 12.dp), color = white, fontSize = 13.sp)
                    Text("＋ Ma liste", Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A2A3B))
                        .clickable(onClick = onFavorites).padding(horizontal = 14.dp, vertical = 12.dp),
                        color = white, fontSize = 13.sp)
                }
            }
        }
        Text("Applications favorites", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            fontSize = 19.sp, color = white, fontWeight = FontWeight.Bold)
        val shortcuts = listOf(
            Triple("Direct TV", Color(0xFF034B90), onLive),
            Triple("Guide TV", Color(0xFF11355B), onGuide),
            Triple("Films", Color(0xFF351455), onVod),
            Triple("Séries", Color(0xFF065057), onSeries),
            Triple("Favoris", Color(0xFF5A2336), onFavorites),
            Triple("Recherche IA", Color(0xFF242568), onSearch),
            Triple("Réglages", Color(0xFF27364B), onSettings)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shortcuts) { shortcut ->
                Box(Modifier.width(103.dp).height(82.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(shortcut.second.copy(alpha = 0.67f), shortcut.second)))
                    .border(1.dp, if (shortcut.first == "Direct TV") blue else white.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickable(onClick = shortcut.third), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        NextShortcutIcon(shortcut.first, white)
                        Text(shortcut.first, color = white, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        MobilePosterRail("Tendances sur TMDB", state.recommendedVod.ifEmpty { state.recentVodReleases }, onVodItem)
        MobilePosterRail("Nouveautés films", state.recentVodReleases, onVodItem)
        MobileSeriesRail("Séries à découvrir", state.recommendedSeries.ifEmpty { state.recentSeriesReleases }, onSeriesItem)
        if (state.recentVodReleases.isEmpty() && state.recentSeriesReleases.isEmpty()) {
            Text("Connectez votre service IPTV pour retrouver vos films et séries ici.",
                color = white.copy(alpha = 0.75f), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun MobilePosterRail(title: String, items: List<VodItem>, onClick: (VodItem) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp), color = Color.White,
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(items.filter { !it.posterUrl.isNullOrBlank() }.distinctBy { it.streamId }, key = { it.streamId }) { film ->
                Column(Modifier.width(90.dp).clickable { onClick(film) }) {
                    NextPosterImage(film.posterUrl, film.name)
                    Text(film.name, color = Color.White, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun MobileSeriesRail(title: String, entries: List<SeriesItem>, onClick: (SeriesItem) -> Unit) {
    if (entries.isEmpty()) return
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp),
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(entries.filter { !it.coverUrl.isNullOrBlank() }.distinctBy { it.seriesId }, key = { it.seriesId }) { entry ->
                Column(Modifier.width(90.dp).clickable { onClick(entry) }) {
                    NextPosterImage(entry.coverUrl, entry.name)
                    Text(entry.name, color = Color.White, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

private fun isGoodFeaturedTitle(name: String): Boolean {
    val value = name.trim()
    if (value.length !in 3..52) return false
    if (value.any { it in '\u0600'..'\u06FF' || it in '\u0900'..'\u097F' }) return false
    if (value.startsWith("AR -", true) || value.startsWith("AF -", true) ||
        value.startsWith("DE -", true) || value.startsWith("ES -", true)) return false
    if (value.contains("http", true) || value.contains("XXX", true)) return false
    return value.any { it.isLetter() }
}

@Composable
private fun NextPosterImage(url: String?, title: String) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(9.dp))
        .background(Color(0xFF162335)), contentAlignment = Alignment.Center) {
        if (failed) {
            Text(title.take(2).uppercase(), color = Color(0xFF9CCBFF),
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else {
            AsyncImage(model = url, contentDescription = title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onError = { failed = true })
        }
    }
}
private fun cleanNextDisplayTitle(raw: String): String = raw.trim()
    .replace(Regex("^(?:FR|EN|VF|VOSTFR|MULTI)\\s*[-:|]\\s*", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s*\\((?:19|20)\\d{2}\\)\\s*$"), "")
    .trim().ifBlank { raw.trim() }

private fun isReadableNextSynopsis(raw: String): Boolean =
    raw.isNotBlank() && raw.none { it in '\u0600'..'\u06FF' || it in '\u0900'..'\u097F' }

@Composable
private fun NextShortcutIcon(label: String, tint: Color) {
    Canvas(Modifier.size(27.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.7.dp.toPx())
        when (label) {
            "Direct TV" -> {
                drawRoundRect(tint, topLeft = Offset(w * .12f, h * .20f),
                    size = Size(w * .76f, h * .56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(tint, Offset(w * .35f, h * .84f), Offset(w * .65f, h * .84f), stroke.width)
            }
            "Guide TV" -> {
                drawRoundRect(tint, topLeft = Offset(w * .12f, h * .13f),
                    size = Size(w * .76f, h * .74f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = stroke)
                for (i in 1..2) {
                    val y = h * (.13f + .74f * i / 3f)
                    drawLine(tint, Offset(w * .12f, y), Offset(w * .88f, y), stroke.width)
                }
                drawLine(tint, Offset(w * .49f, h * .13f), Offset(w * .49f, h * .87f), stroke.width)
            }
            "Films" -> {
                val path = Path().apply {
                    moveTo(w * .29f, h * .13f); lineTo(w * .80f, h * .50f)
                    lineTo(w * .29f, h * .87f); close()
                }
                drawPath(path, tint)
            }
            "Favoris" -> {
                val path = Path().apply {
                    moveTo(w * .5f, h * .85f)
                    cubicTo(-w * .25f, h * .40f, w * .05f, -h * .08f, w * .5f, h * .24f)
                    cubicTo(w * .95f, -h * .08f, w * 1.25f, h * .40f, w * .5f, h * .85f)
                }
                drawPath(path, tint, style = stroke)
            }
            "Recherche IA" -> {
                drawCircle(tint, radius = w * .27f, center = Offset(w * .43f, h * .40f), style = stroke)
                drawLine(tint, Offset(w * .63f, h * .62f), Offset(w * .88f, h * .88f), stroke.width)
            }
            "Réglages" -> {
                drawCircle(tint, radius = w * .33f, style = stroke)
                drawCircle(tint, radius = w * .11f)
                for (i in 0..7) {
                    val angle = Math.PI * i / 4
                    val dx = kotlin.math.cos(angle).toFloat()
                    val dy = kotlin.math.sin(angle).toFloat()
                    drawLine(tint, Offset(w * .5f + dx * w * .34f, h * .5f + dy * h * .34f),
                        Offset(w * .5f + dx * w * .47f, h * .5f + dy * h * .47f), stroke.width)
                }
            }
            else -> {
                drawRoundRect(tint, topLeft = Offset(w * .22f, h * .12f),
                    size = Size(w * .56f, h * .76f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = stroke)
                for (i in 1..3) drawLine(tint, Offset(w * .30f, h * (.15f + i * .16f)),
                    Offset(w * .7f, h * (.15f + i * .16f)), stroke.width)
            }
        }
    }
}
