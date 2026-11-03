package com.tvflow.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Every action targets libVLC, including when Media3 exists but is inactive. */
@Composable
internal fun VlcVodControls(
    controller: VlcPlaybackController,
    title: String,
    modifier: Modifier = Modifier,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var scrubbing by remember(controller) { mutableStateOf<Float?>(null) }
    var trackDialog by remember(controller) { mutableStateOf<String?>(null) }
    val fraction = if (state.durationMs > 0) (state.positionMs.toDouble() / state.durationMs).toFloat().coerceIn(0f, 1f) else 0f
    val shownPosition = scrubbing?.let { (it * state.durationMs).toLong() } ?: state.positionMs

    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(Color(0xEB07111E)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(vodTimeLabel(shownPosition), color = Color.White)
            Text(vodTimeLabel(state.durationMs), color = Color.White)
        }
        Slider(
            value = scrubbing ?: fraction,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { controller.seekTo((it * state.durationMs).toLong()) }
                scrubbing = null
            },
            enabled = state.canSeek,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { controller.seekBy(-10_000) }, enabled = state.canSeek) { Text("−10 s") }
            Button(onClick = controller::togglePlaying) { Text(if (state.playRequested) "Pause" else "Reprendre") }
            TextButton(onClick = { controller.seekBy(10_000) }, enabled = state.canSeek) { Text("+10 s") }
            TextButton(onClick = { trackDialog = "audio" }) { Text("Audio") }
            TextButton(onClick = { trackDialog = "subtitles" }) { Text("Sous-titres") }
            onPreviousEpisode?.let { TextButton(onClick = it) { Text("Épisode précédent") } }
            onNextEpisode?.let { TextButton(onClick = it) { Text("Épisode suivant") } }
        }
        state.commandError?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall) }
    }

    trackDialog?.let { kind ->
        val audio = kind == "audio"
        val choices = if (audio) state.audioTracks else
            listOf(VlcTrack(-1, "Désactivés")) + state.subtitleTracks.filter { it.id != -1 }
        val selected = if (audio) state.audioTrackId else state.subtitleTrackId
        AlertDialog(
            onDismissRequest = { trackDialog = null },
            title = { Text(if (audio) "Pistes audio" else "Sous-titres") },
            text = {
                Column {
                    if (choices.isEmpty()) Text("Aucune piste disponible sur ce flux.")
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(choices, key = { it.id }) { track ->
                            TextButton(onClick = {
                                controller.clearError()
                                if (audio) controller.selectAudio(track.id) else controller.selectSubtitle(track.id)
                                trackDialog = null
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text((if (track.id == selected) "✓  " else "") + track.label,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { trackDialog = null }) { Text("Fermer") } }
        )
    }
}
