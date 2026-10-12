package com.tvflow.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val live = results.live
    val vod = results.vod
    val series = results.series

    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text("Recherche globale", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            label = { Text("Chaîne, film ou série") },
            singleLine = true
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live.isNotEmpty()) item { Text("TV en direct", style = MaterialTheme.typography.titleLarge) }
            items(live, key = { "live:${it.streamId}" }) { item -> ResultRow(item.name, "TV") { onLive(item) } }
            if (vod.isNotEmpty()) item { Text("Films", style = MaterialTheme.typography.titleLarge) }
            items(vod, key = { "vod:${it.streamId}" }) { item -> ResultRow(item.name, "Film") { onVod(item) } }
            if (series.isNotEmpty()) item { Text("Séries", style = MaterialTheme.typography.titleLarge) }
            items(series, key = { "series:${it.seriesId}" }) { item -> ResultRow(item.name, "Série") { onSeries(item) } }
        }
    }
}

@Composable
private fun ResultRow(title: String, type: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(type, style = MaterialTheme.typography.bodySmall)
        }
    }
}
