package com.tvflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem

private val nextBlue = Color(0xFF078AFF)
private val nextWhite = Color(0xFFE9F0FF)
private val nextBg = Color(0xFF030910)

@Composable
internal fun NextCinematicDashboard(
    mobile: Boolean = false,
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
    if (mobile) {
        BoxWithConstraints(Modifier.fillMaxSize().background(nextBg).statusBarsPadding().navigationBarsPadding()) {
            val factor = minOf(maxWidth.value / 1400f, maxHeight.value / 620f)
            Box(
                Modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart).requiredSize(1400.dp, 620.dp)
                    .graphicsLayer {
                        scaleX = factor
                        scaleY = factor
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                NextCinematicDashboard(
                    mobile = false, state = state, onLive = onLive, onGuide = onGuide,
                    onVod = onVod, onSeries = onSeries, onSearch = onSearch,
                    onSettings = onSettings, onFavorites = onFavorites,
                    onVodItem = onVodItem, onSeriesItem = onSeriesItem
                )
            }
        }
        return
    }
    val series = (state.recommendedSeries + state.recentSeriesReleases).firstOrNull { isNextHeroCandidate(it.name) && !it.coverUrl.isNullOrBlank() }
    val film = if (series == null) (state.recommendedVod + state.recentVodReleases).firstOrNull { isNextHeroCandidate(it.name) && !it.posterUrl.isNullOrBlank() } else null
    val title = cleanNextHeroName(series?.name ?: film?.name ?: "Bienvenue sur TVFlow")
    val description = (series?.plot ?: film?.synopsis).orEmpty().takeIf { isNextHeroDescriptionReadable(it) }
        ?: "Découvrez cette sélection dans votre catalogue TVFlow."
    val image = state.featuredBackdropUrl ?: series?.coverUrl ?: film?.posterUrl
    val actions = listOf(
        Triple("⌂  Accueil", true, {}),
        Triple("▣  Direct", false, onLive),
        Triple("▦  Guide TV", false, onGuide),
        Triple("▣  Films", false, onVod),
        Triple("▣  Séries", false, onSeries),
        Triple("⌕  Recherche", false, onSearch),
        Triple("⚙  Réglages", false, onSettings)
    )
    Box(Modifier.fillMaxSize().background(nextBg)) {
        if (!image.isNullOrBlank()) {
            // A catalog poster must not masquerade as a widescreen TMDB backdrop.
            // Blurred ambient layer preserves a cinematic look when cloud metadata is unavailable.
            if (state.featuredBackdropUrl == null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.84f).fillMaxHeight(0.59f)
                        .align(Alignment.TopEnd).blur(36.dp)
                )
            }
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = if (state.featuredBackdropUrl == null) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(if (state.featuredBackdropUrl == null) 0.46f else 0.82f)
                    .fillMaxHeight(0.58f).align(Alignment.TopEnd)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(nextBg, nextBg.copy(alpha = 0.82f), nextBg.copy(alpha = 0.12f))
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, nextBg.copy(alpha = 0.18f), nextBg), startY = 140f
        )))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(136.dp)) {
                    Text("TVFlow", color = nextWhite, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("TVFlow-v2 Next", color = nextWhite.copy(alpha = 0.75f), fontSize = 12.sp)
                }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                        .background(Color(0xC8111A27)).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions.forEach { (label, active, callback) ->
                        NextFocusSurface(
                            onClick = callback,
                            modifier = Modifier.padding(2.dp),
                            base = if (active) Color(0xFF005DCF) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(label, Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                color = Color.White, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                Text("  ⌕    ◉", color = nextWhite, fontSize = 19.sp)
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth().height(178.dp)) {
                Column(Modifier.width(140.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    listOf("♥   Mes favoris" to onFavorites,
                        "◷   Derniers ajoutés" to onVod,
                        "♨   Tendances" to onVod,
                        "★   Recommandés" to onSeries
                    ).forEach { (label, callback) ->
                        NextFocusSurface(onClick = callback, base = Color.Transparent) {
                            Text(label, color = nextWhite.copy(alpha = 0.88f), fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 5.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (series != null) "★  SÉRIE" else "★  FILM",
                        color = nextWhite, fontSize = 10.sp,
                        modifier = Modifier.background(Color(0xCC172333), RoundedCornerShape(3.dp)).padding(4.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(title.substringBeforeLast(" (20").removePrefix("EN - ").removePrefix("FR - ").uppercase(), color = Color.White, fontSize = 31.sp,
                        fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull((series?.releaseYear ?: film?.releaseYear)?.toString(),
                            if (series != null) "Série" else "Film", "HD", "TVFlow").joinToString("   "),
                        fontSize = 12.sp, color = Color(0xFF4CCBA2)
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(description, color = nextWhite.copy(alpha = 0.84f), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.64f))
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NextAction("▶  Voir maintenant", nextBlue) {
                            if (series != null) onSeriesItem(series) else if (film != null) onVodItem(film)
                        }
                        NextAction("＋  Ma liste", Color(0xC5233144), onFavorites)
                        NextAction("ⓘ  Détails", Color(0xC5233144)) {
                            if (series != null) onSeriesItem(series) else if (film != null) onVodItem(film)
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text("Applications favorites", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("▣\nDirect TV", Color(0xFF003D84), onLive),
                    Triple("▦\nGuide TV", Color(0xFF05274F), onGuide),
                    Triple("▣\nFilms", Color(0xFF2F1048), onVod),
                    Triple("▣\nSéries", Color(0xFF003C49), onSeries),
                    Triple("♥\nMes favoris", Color(0xFF451A25), onFavorites),
                    Triple("⌕\nRecherche IA", Color(0xFF171B58), onSearch),
                    Triple("◉\nTMDB", Color(0xFF062C67), onVod),
                    Triple("⚙\nRéglages", Color(0xFF1E2834), onSettings)
                ).forEachIndexed { index, tile ->
                    NextFocusSurface(
                        modifier = Modifier.weight(1f).height(57.dp),
                        onClick = tile.third, base = tile.second,
                        selected = index == 0
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(tile.first, color = nextWhite, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            NextMediaRow("Recommandés pour vous",
                state.recommendedVod.ifEmpty { state.recentVodReleases }, onVodItem)
            Spacer(Modifier.height(9.dp))
            NextMediaRow("Nouveautés films", state.recentVodReleases, onVodItem)
        }
    }
}

@Composable
private fun NextMediaRow(title: String, entries: List<VodItem>, onClick: (VodItem) -> Unit) {
    Column {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.asSequence()
                .filter { isNextCatalogCard(it.name, it.posterUrl) }
                .distinctBy { it.streamId }
                .sortedBy { nextCatalogDisplayPriority(it.name) }
                .take(60).toList(), key = { it.streamId }) { vod ->
                NextFocusSurface(modifier = Modifier.width(130.dp).height(93.dp), onClick = { onClick(vod) },
                    base = Color(0xFF132232)) {
                    Box {
                        AsyncImage(vod.posterUrl, contentDescription = vod.name,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxWidth().height(32.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f)))))
                        Text(cleanNextHeroName(vod.name), color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NextAction(label: String, color: Color, onClick: () -> Unit) {
    NextFocusSurface(onClick = onClick, base = color) {
        Text(label, Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NextFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    base: Color,
    selected: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier.clip(shape).background(base)
            .border(if (focused || selected) 2.dp else 1.dp,
                if (focused || selected) nextBlue else Color.White.copy(alpha = .10f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

private fun isNextHeroCandidate(name: String): Boolean {
    val value = name.trim()
    if (value.length !in 4..56) return false
    if (value.startsWith("AR -", true) || value.startsWith("AF -", true) ||
        value.startsWith("DE -", true) || value.startsWith("ES -", true)) return false
    return value.none { it in '\u0600'..'\u06FF' || it in '\u0900'..'\u097F' }
}

private fun cleanNextHeroName(name: String): String =
    name.trim().replace(Regex("^(?:FR|EN|VF|MULTI|VOSTFR)\\s*[-:|]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\((?:19|20)\\d{2}\\)(?:\\s*\\([A-Z]{2}\\))?$"), "")
        .trim()

private fun isNextHeroDescriptionReadable(text: String): Boolean =
    text.isNotBlank() && text.none { it in '\u0600'..'\u06FF' || it in '\u0900'..'\u097F' }

private fun isNextCatalogCard(name: String, poster: String?): Boolean {
    val title = name.trim()
    return poster?.startsWith("http", ignoreCase = true) == true &&
        title.length in 3..85 &&
        !listOf("[X]", "[XXX]", "XXX", "BRAZZERS", "JULESJORDAN", "PORNO", "PORN").any { title.contains(it, ignoreCase = true) } &&
        !listOf("AR -", "AF -", "DE -", "DK -", "IN -").any { title.startsWith(it, ignoreCase = true) } &&
        title.none { it in '\u0600'..'\u06FF' || it in '\u0900'..'\u097F' }
}

private fun nextCatalogDisplayPriority(name: String): Int {
    val title = name.trim()
    return when {
        title.startsWith("FR -", ignoreCase = true) -> 0
        title.startsWith("VF -", ignoreCase = true) -> 1
        title.startsWith("EN -", ignoreCase = true) -> 3
        Regex("^[A-Z]{2}\\s*[-|]").containsMatchIn(title) -> 4
        else -> 2
    }
}
