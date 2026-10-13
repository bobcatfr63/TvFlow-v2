package com.tvflow.app.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.SeriesItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tvflow.app.domain.VodItem

private val suggestions = listOf(
    "Une comédie française",
    "Un film d'action avec du suspense",
    "Une série policière",
    "Un film avec une invasion extraterrestre",
    "Est-ce qu'il y a un film d'action ce soir ?",
    "Quel documentaire passe ce soir ?",
    "Y a-t-il du foot en direct ?"
)

@Composable
fun TvFlowAiScreen(
    onVod: (VodItem) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onLive: (LiveChannel) -> Unit,
    viewModel: TvFlowAiViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val result by viewModel.results.collectAsState()
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { viewModel.query.value = it }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("✨ TVFlow AI", style = MaterialTheme.typography.headlineSmall)
        Text("Films, séries et programmes TV du serveur actif", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Quel film ou quelle série aimerais-tu voir ?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            }
            if (intent.resolveActivity(context.packageManager) != null) voiceLauncher.launch(intent)
        }) { Text("🎙 Recherche vocale") }
        if (query.isBlank()) {
            Text("Quelques idées", style = MaterialTheme.typography.titleMedium)
            suggestions.forEach { suggestion ->
                Button(onClick = { viewModel.query.value = suggestion }) { Text(suggestion) }
            }
        }
        if (TvProgramMatcher.isTvQuestion(query) && !result.tvQuestion) {
            Text("Recherche des programmes dans le guide TV…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        result.footballAnswer?.let { answer ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Calendrier sportif en ligne", style = MaterialTheme.typography.titleMedium)
                    Text(answer.message)
                    if (answer.eventName != null && result.programs.isEmpty()) {
                        Text("Match annoncé, mais aucune chaîne correspondante confirmée dans ton EPG.", color = MaterialTheme.colorScheme.secondary)
                    } else if (answer.eventName != null && result.programs.isNotEmpty()) {
                        Text("Programme correspondant trouvé dans ton EPG : sélectionne la chaîne ci-dessous.", color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("Source : TheSportsDB. Programmation et disponibilité des chaînes vérifiées séparément dans TVFlow.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        result.notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result.tvQuestion && result.programs.isEmpty() && result.footballAnswer == null) {
                item { Text("Aucune diffusion correspondante confirmée dans l'EPG du serveur actif. Les programmes sans guide TV ne sont pas vérifiables ici.") }
            }
            if (result.programs.isNotEmpty()) {
                item { Text("À la télévision selon ton guide EPG", style = MaterialTheme.typography.titleLarge) }
                items(result.programs, key = { "epg:${it.first.streamId}:${it.first.startTimestamp}" }) { (program, channel) ->
                    Card(onClick = { onLive(channel) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(program.title, style = MaterialTheme.typography.titleMedium)
                            val formatter = SimpleDateFormat("HH:mm", Locale.FRANCE)
                            Text("${channel.name} · ${formatter.format(Date(program.startTimestamp * 1000))} - ${formatter.format(Date(program.stopTimestamp * 1000))}")
                            program.description?.takeIf { it.isNotBlank() }?.let { Text(it.take(180)) }
                            Text("Ouvrir la chaîne", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (!TvProgramMatcher.isTvQuestion(query) && !result.tvQuestion && query.isNotBlank() && result.movies.isEmpty() && result.series.isEmpty() && result.error == null) {
                item { Text("Aucun résultat confirmé dans le catalogue. Essaie un titre ou un genre précis.") }
            }
            if (result.movies.isNotEmpty()) item { Text("Films référencés dans ton catalogue", style = MaterialTheme.typography.titleLarge) }
            items(result.movies, key = { "movie:${it.streamId}" }) { movie ->
                Card(onClick = { onVod(movie) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        movie.posterUrl?.let { url ->
                            AsyncImage(model = url, contentDescription = null, modifier = Modifier.width(72.dp).height(104.dp))
                        }
                        Column {
                            Text(movie.name, style = MaterialTheme.typography.titleMedium)
                            movie.releaseYear?.let { Text(it.toString()) }
                            movie.synopsis?.takeIf { it.isNotBlank() }?.let { Text(it.take(180)) }
                        }
                    }
                }
            }
            if (result.series.isNotEmpty()) item { Text("Séries disponibles", style = MaterialTheme.typography.titleLarge) }
            items(result.series, key = { "series:${it.seriesId}" }) { series ->
                Card(onClick = { onSeries(series) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        series.coverUrl?.let { url ->
                            AsyncImage(model = url, contentDescription = null, modifier = Modifier.width(72.dp).height(104.dp))
                        }
                        Column {
                            Text(series.name, style = MaterialTheme.typography.titleMedium)
                            series.plot?.takeIf { it.isNotBlank() }?.let { Text(it.take(180)) }
                        }
                    }
                }
            }
        }
    }
}
