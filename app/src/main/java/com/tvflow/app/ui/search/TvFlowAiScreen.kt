package com.tvflow.app.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.tvflow.app.ui.theme.rainbowBorder

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
    val suggestionGridState = rememberLazyGridState()
    LaunchedEffect(result.searchedQuery) {
        if (result.searchedQuery.isNotBlank()) listState.scrollToItem(0)
    }
    LaunchedEffect(query.isBlank()) {
        if (query.isBlank()) suggestionGridState.scrollToItem(0)
    }
    var inputFocused by remember { mutableStateOf(false) }
    val microphoneFocus = remember { FocusRequester() }
    val firstSuggestionFocus = remember { FocusRequester() }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { viewModel.query.value = it; voiceStatus = null } ?: run { voiceStatus = "Aucune parole reconnue. Réessaie ou saisis une recherche." }
        } else { voiceStatus = "Reconnaissance vocale annulée ou indisponible sur cette box." }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14324A), Color(0xFF07121E), Color(0xFF040911))))
            .padding(horizontal = 20.dp, vertical = if (query.isBlank()) 12.dp else 6.dp)
            .onPreviewKeyEvent { event ->
                if (inputFocused && event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                    microphoneFocus.requestFocus()
                    true
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(if (query.isBlank()) 10.dp else 6.dp)
    ) {
        Text("✦ TVFlow AI", style = if (query.isBlank()) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = Color(0xFFF4FAFF), fontWeight = FontWeight.Bold)
        if (query.isBlank()) {
            Text("Trouve ton prochain programme", style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF65DFFF))
            Text("Films, séries et programmes TV de ton catalogue", style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCEE2EF))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Quel film ou quelle série aimerais-tu voir ?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { inputFocused = it.isFocused }
                .focusProperties { down = microphoneFocus }
                .rainbowBorder(inputFocused, 6.dp)
        )
        Button(modifier = Modifier.focusRequester(microphoneFocus).rainbowBorder(true, 24.dp).focusProperties {
            if (query.isBlank()) down = firstSuggestionFocus
        }, onClick = {
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
            Text("Quelques idées", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF4FAFF), fontWeight = FontWeight.SemiBold)
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
        if (query.isBlank() && !searching) result.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (query.isBlank()) {
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val columns = if (maxWidth >= 720.dp) 3 else if (maxWidth >= 460.dp) 2 else 1
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                    state = suggestionGridState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suggestions.size) { index ->
                        val suggestion = suggestions[index]
                        var focused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.query.value = suggestion },
                            modifier = Modifier.fillMaxWidth().height(76.dp)
                                .then(if (index == 0) Modifier.focusRequester(firstSuggestionFocus) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .border(if (focused) 4.dp else 1.dp,
                                    if (focused) Color(0xFF42E8FF) else Color(0xFF367698),
                                    androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                                .rainbowBorder(focused, 18.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (focused) Color(0xFF126A8F) else Color(0xFF17334A),
                                contentColor = Color.White
                            )
                        ) { Text(suggestion, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        if (query.isNotBlank()) LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!searching) result.notice?.let { notice ->
                item { Text(notice, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
            }
            if (!searching) result.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (!searching && result.tvQuestion && result.programs.isEmpty() && result.footballAnswer == null) {
                item { Text("Aucune diffusion confirmée dans l'EPG du serveur actif.", color = MaterialTheme.colorScheme.onSurface) }
            }
            if (!searching && result.programs.isNotEmpty()) {
                item { Text("À la télévision selon ton guide EPG", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF4FAFF), fontWeight = FontWeight.Bold) }
                items(result.programs, key = { "epg:${it.first.streamId}:${it.first.startTimestamp}" }) { (program, channel) ->
                    var focused by remember { mutableStateOf(false) }
                    Card(onClick = { onLive(channel) }, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF42E8FF) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
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
            if (!searching && result.movies.isNotEmpty()) item { Text("Films dans ton catalogue", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF4FAFF), fontWeight = FontWeight.Bold) }
            items(if (searching) emptyList() else result.movies, key = { "movie:${it.streamId}" }) { movie ->
                var focused by remember { mutableStateOf(false) }
                Card(onClick = { onVod(movie) }, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF42E8FF) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
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
            if (!searching && result.series.isNotEmpty()) item { Text("Séries disponibles", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF4FAFF), fontWeight = FontWeight.Bold) }
            items(if (searching) emptyList() else result.series, key = { "series:${it.seriesId}" }) { series ->
                var focused by remember { mutableStateOf(false) }
                Card(onClick = { onSeries(series) }, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF42E8FF) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
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
