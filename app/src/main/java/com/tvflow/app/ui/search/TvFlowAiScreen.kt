package com.tvflow.app.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.CircularProgressIndicator
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
    val searching = query.trim().length >= 2 && result.searchedQuery != query
    val context = LocalContext.current
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { viewModel.query.value = it; voiceStatus = null } ?: run { voiceStatus = "Aucune parole reconnue. Réessaie ou saisis une recherche." }
        } else { voiceStatus = "Reconnaissance vocale annulée ou indisponible sur cette box." }
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
            try {
                voiceLauncher.launch(intent)
                voiceStatus = "Parle après le signal de reconnaissance vocale."
            } catch (e: ActivityNotFoundException) {
                voiceStatus = "Aucun service de reconnaissance vocale installé sur cet appareil."
            } catch (e: SecurityException) {
                voiceStatus = "Reconnaissance vocale interdite par Android sur cet appareil."
            }
        }) { Text("🎙 Recherche vocale") }
        voiceStatus?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        if (query.isBlank()) {
            Text("Quelques idées", style = MaterialTheme.typography.titleMedium)
            // Keep suggestions inside the same scroll container as the results.
        }
        if (searching) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 3.dp)
                Text("TVFlow AI recherche dans ton catalogue et les sources disponibles…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (query.length >= 2 && result.error == null) {
            Text("Recherche terminée", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        if (!searching) result.footballAnswer?.let { answer ->
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
        if (!searching) result.notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        if (!searching) result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (query.isBlank()) items(suggestions) { suggestion ->
                var focused by remember { mutableStateOf(false) }
                Button(
                    onClick = { viewModel.query.value = suggestion },
                    modifier = Modifier.onFocusChanged { focused = it.isFocused }.border(
                        if (focused) 3.dp else 0.dp,
                        if (focused) Color.White else Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary)
                ) { Text(suggestion) }
            }
            if (!searching && result.tvQuestion && result.programs.isEmpty() && result.footballAnswer == null) {
                item { Text("Aucune diffusion confirmée dans l'EPG du serveur actif.", color = MaterialTheme.colorScheme.onSurface) }
            }
            if (!searching && result.programs.isNotEmpty()) {
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
            if (!searching && !TvProgramMatcher.isTvQuestion(query) && !result.tvQuestion && query.isNotBlank() && result.movies.isEmpty() && result.series.isEmpty() && result.error == null) {
                item { Text("Recherche terminée : aucun résultat confirmé sur le serveur actif. Pour les thèmes (informatique, piratage, science…), essaie aussi un mot-clé présent dans un titre ou un synopsis.", color = MaterialTheme.colorScheme.onSurface) }
            }
            if (!searching && result.movies.isNotEmpty()) item { Text("Films référencés dans ton catalogue", style = MaterialTheme.typography.titleLarge) }
            items(if (searching) emptyList() else result.movies, key = { "movie:${it.streamId}" }) { movie ->
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
            if (!searching && result.series.isNotEmpty()) item { Text("Séries disponibles", style = MaterialTheme.typography.titleLarge) }
            items(if (searching) emptyList() else result.series, key = { "series:${it.seriesId}" }) { series ->
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
