package com.tvflow.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.tvflow.app.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem

@Composable
fun SearchScreen(
    onLive: (LiveChannel) -> Unit,
    onVod: (VodItem) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onAiSearch: (() -> Unit)? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val isNext = BuildConfig.APPLICATION_ID.endsWith(".next")
    val uiState = if (isNext) viewModel.nextState.collectAsState().value
        else NextSearchState(results = viewModel.results.collectAsState().value)
    val results = uiState.results
    val live = results.live
    val vod = results.vod
    val series = results.series

    val portrait = com.tvflow.app.ui.common.isNextPortraitLayout()
    Column(Modifier.fillMaxSize().then(if (portrait) Modifier.imePadding() else Modifier)
        .padding(if (portrait) 16.dp else 32.dp)) {
        if (portrait) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recherche globale", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                if (onAiSearch != null) Button(onClick = onAiSearch) { Text("Recherche IA", maxLines = 1) }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recherche globale", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            if (onAiSearch != null) Button(onClick = onAiSearch) { Text("Recherche IA") }
        }
        Text(if (isNext) "Chaînes, films et séries de votre catalogue" else "🔎 Recherche locale · Groq non utilisé sur cet écran",
            style = MaterialTheme.typography.labelMedium, color = Color(0xFF9FE6FF))
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            label = { Text("Chaîne, film ou série") },
            singleLine = true
        )
        if (isNext) {
            when {
                uiState.loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Recherche en cours…", color = Color.White)
                }
                uiState.error != null -> Text(uiState.error, color = Color(0xFFFFB4AB))
                query.trim().length < 2 -> Text("Saisissez au moins 2 caractères.", color = Color(0xFFB7C7DA))
                live.isEmpty() && vod.isEmpty() && series.isEmpty() -> Text("Aucun résultat dans votre catalogue.", color = Color(0xFFB7C7DA))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live.isNotEmpty()) item { Text("TV en direct", style = MaterialTheme.typography.titleLarge, color = if (isNext) Color(0xFFE8F2FF) else Color.Unspecified) }
            items(live, key = { "live:${it.streamId}" }) { item -> ResultRow(item.name, "TV") { onLive(item) } }
            if (vod.isNotEmpty()) item { Text("Films", style = MaterialTheme.typography.titleLarge, color = if (isNext) Color(0xFFE8F2FF) else Color.Unspecified) }
            items(vod, key = { "vod:${it.streamId}" }) { item -> ResultRow(item.name, "Film") { onVod(item) } }
            if (series.isNotEmpty()) item { Text("Séries", style = MaterialTheme.typography.titleLarge, color = if (isNext) Color(0xFFE8F2FF) else Color.Unspecified) }
            items(series, key = { "series:${it.seriesId}" }) { item -> ResultRow(item.name, "Série") { onSeries(item) } }
        }
    }
}

@Composable
private fun ResultRow(title: String, type: String, onClick: () -> Unit) {
    val isNext = BuildConfig.APPLICATION_ID.endsWith(".next")
    var focused by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        colors = if (isNext) CardDefaults.cardColors(containerColor = Color(0xFF102437), contentColor = Color.White) else CardDefaults.cardColors(),
        border = if (isNext) BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF67D6FF) else Color(0xFF31445C)) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = if (isNext) Modifier.weight(1f).padding(end = 12.dp) else Modifier,
                color = if (isNext) Color.White else Color.Unspecified,
                maxLines = if (isNext) 2 else Int.MAX_VALUE,
                overflow = if (isNext) TextOverflow.Ellipsis else TextOverflow.Clip)
            Text(type, style = MaterialTheme.typography.bodySmall,
                color = if (isNext) Color(0xFF8BD7FF) else Color.Unspecified)
        }
    }
}
