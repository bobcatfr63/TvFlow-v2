package com.tvflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val series = state.recommendedSeries.firstOrNull() ?: state.recentSeriesReleases.firstOrNull()
    val film = state.recommendedVod.firstOrNull() ?: state.recentVodReleases.firstOrNull()
    val title = series?.name ?: film?.name ?: "Bienvenue sur TVFlow"
    val image = series?.coverUrl ?: film?.posterUrl
    val white = Color(0xFFF3F6FF)
    val blue = Color(0xFF078AFF)
    Column(Modifier.fillMaxSize().background(Color(0xFF040910)).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TVFlow", color = white, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("TVFlow-v2 Next", color = blue, fontSize = 12.sp)
            }
            Text("⌕", Modifier.clickable(onClick = onSearch).padding(10.dp), color = white, fontSize = 24.sp)
            Text("⚙", Modifier.clickable(onClick = onSettings).padding(10.dp), color = white, fontSize = 23.sp)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Accueil" to {}, "Direct" to onLive, "Guide TV" to onGuide, "Films" to onVod, "Séries" to onSeries, "Favoris" to onFavorites).forEachIndexed { i, entry ->
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (i == 0) blue else Color(0xFF172536))
                    .clickable(onClick = entry.second).padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(entry.first, color = white, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            if (!image.isNullOrBlank()) {
                AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0x66040910), Color(0x55040910), Color(0xFF040910)))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(if (series != null) "SÉRIE À LA UNE" else "FILM À LA UNE", color = blue,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(title, color = white, fontWeight = FontWeight.ExtraBold, fontSize = 29.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("▶  Voir maintenant", Modifier.clip(RoundedCornerShape(10.dp)).background(blue)
                        .clickable { if (series != null) onSeriesItem(series) else if (film != null) onVodItem(film) }
                        .padding(horizontal = 14.dp, vertical = 12.dp), color = white, fontSize = 13.sp)
                    Text("＋ Ma liste", Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A2A3B))
                        .clickable(onClick = onFavorites).padding(horizontal = 14.dp, vertical = 12.dp),
                        color = white, fontSize = 13.sp)
                }
            }
        }
        Text("Applications favorites", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 10.dp),
            fontSize = 19.sp, color = white, fontWeight = FontWeight.Bold)
        val shortcuts = listOf(
            Triple("▣ Direct TV", Color(0xFF034B90), onLive),
            Triple("▦ Guide TV", Color(0xFF11355B), onGuide),
            Triple("▣ Films", Color(0xFF351455), onVod),
            Triple("▣ Séries", Color(0xFF065057), onSeries),
            Triple("♥ Favoris", Color(0xFF5A2336), onFavorites),
            Triple("⌕ Recherche IA", Color(0xFF242568), onSearch),
            Triple("⚙ Réglages", Color(0xFF27364B), onSettings)
        )
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { shortcut ->
                        Box(Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(13.dp))
                            .background(shortcut.second).clickable(onClick = shortcut.third),
                            contentAlignment = Alignment.Center) {
                            Text(shortcut.first, color = white, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        MobilePosterRail("Tendances sur TMDB", state.recommendedVod.ifEmpty { state.recentVodReleases }, onVodItem)
        MobilePosterRail("Nouveautés films", state.recentVodReleases, onVodItem)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MobilePosterRail(title: String, items: List<VodItem>, onClick: (VodItem) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp), color = Color.White,
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(items, key = { it.streamId }) { film ->
                Column(Modifier.width(112.dp).clickable { onClick(film) }) {
                    AsyncImage(model = film.posterUrl, contentDescription = film.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth()
                            .height(151.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF162335)))
                    Text(film.name, color = Color.White, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}
