package com.tvflow.app.ui.vod

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import com.tvflow.app.ui.common.AdaptiveBufferPolicy
import com.tvflow.app.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import com.tvflow.app.cast.CastPlaybackEffect
import com.tvflow.app.cast.CastRouteButton
import com.tvflow.app.connect.PlaybackTransfer
import com.tvflow.app.connect.TvFlowSendButton
import com.tvflow.app.domain.Episode
import com.tvflow.app.ui.common.PlayerLifecycleEffect
import com.tvflow.app.ui.common.VlcFallbackPlayer
import com.tvflow.app.util.isTelevisionUi
import com.tvflow.app.ui.common.iptvMediaSourceFactory
import com.tvflow.app.ui.common.shouldSaveVlcProgress
import com.tvflow.app.ui.common.shouldUseVlcFallback
import kotlinx.coroutines.delay

private const val VOD_PLAYER_TAG = "TVFlowVodPlayer"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VodPlayerScreen(
    onPlayEpisode: (Episode, Int) -> Unit = { _, _ -> },
    viewModel: VodPlayerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val playerPrefs by settingsStore.playerPreferences.collectAsState(initial = com.tvflow.app.data.local.PlayerPreferences())
    // Keep a single player for the whole VOD session; changing presets must not interrupt decoding.
    val player = remember {
        val durations = AdaptiveBufferPolicy.durations("AUTO")
        ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(durations.min, durations.max, durations.start, durations.rebuffer)
                .build())
            .setMediaSourceFactory(iptvMediaSourceFactory(context))
            .build()
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var infoVisible by remember { mutableStateOf(false) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    val phoneVlcFirst = remember(context) { !context.isTelevisionUi() }
    var vlcFailedForStream by remember { mutableStateOf(false) }
    var useVlcFallback by remember { mutableStateOf(phoneVlcFirst) }
    var vlcHandoffPositionMs by remember { mutableStateOf(0L) }
    var lastVlcProgressSaveMs by remember { mutableStateOf(0L) }
    var episodeNavigationVisible by remember { mutableStateOf(false) }
    var playbackControlsVisible by remember { mutableStateOf(false) }
    val previousEpisodeFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    var castActive by remember { mutableStateOf(false) }
    val vlcFallbackActive by rememberUpdatedState(useVlcFallback)

    CastPlaybackEffect(
        url = state.streamUrl,
        title = state.title.ifBlank { "TVFlow" },
        subtitle = state.containerExtension?.uppercase(),
        isLive = false,
        startPositionMs = { player.currentPosition.coerceAtLeast(state.resumePositionMs) },
        onCastActiveChanged = { castActive = it }
    )

    LaunchedEffect(castActive, player) {
        if (castActive) player.pause()
    }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    PlayerLifecycleEffect(player)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    VOD_PLAYER_TAG,
                    // Avoid logging the Throwable: HTTP exceptions may contain credential-bearing stream URLs.
                    "Media3 playback failed: code=${error.errorCode}, name=${error.errorCodeName}, cause=${error.cause?.javaClass?.simpleName ?: "unknown"}, media=${state.mediaKey.ifBlank { "unknown" }}"
                )
                var cause: Throwable? = error
                var httpCode: Int? = null
                while (cause != null) {
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cause.responseCode
                        break
                    }
                    cause = cause.cause
                }
                if (!vlcFailedForStream && shouldUseVlcFallback(httpCode, error.errorCodeName)) {
                    vlcHandoffPositionMs = player.currentPosition.coerceAtLeast(state.resumePositionMs)
                    player.pause()
                    useVlcFallback = true
                } else {
                    viewModel.onPlaybackError(httpCode?.let { "HTTP $it" } ?: error.errorCodeName)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(VOD_PLAYER_TAG, "Media3 playing=$isPlaying, state=${player.playbackState}, positionMs=${player.currentPosition.coerceAtLeast(0L)}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.i(VOD_PLAYER_TAG, "Media3 state=$stateName, positionMs=${player.currentPosition.coerceAtLeast(0L)}, durationMs=${player.duration.coerceAtLeast(0L)}")
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.onPlaybackEnded(player.duration.coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(state.streamUrl) {
        vlcFailedForStream = false
        useVlcFallback = phoneVlcFirst
        vlcHandoffPositionMs = 0L
        lastVlcProgressSaveMs = 0L
        val url = state.streamUrl ?: return@LaunchedEffect
        if (useVlcFallback) { player.stop(); player.clearMediaItems(); return@LaunchedEffect }
        Log.i(VOD_PLAYER_TAG, "Preparing Media3: media=${state.mediaKey}, extension=${state.containerExtension ?: "unknown"}, resumeMs=${state.resumePositionMs}")
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (state.resumePositionMs > 0L) player.seekTo(state.resumePositionMs)
        player.playWhenReady = true
    }

    LaunchedEffect(player, state.mediaKey, useVlcFallback) {
        if (state.mediaKey.isBlank() || useVlcFallback) return@LaunchedEffect
        var saveTick = 0
        while (true) {
            delay(1_000L)
            val position = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.coerceAtLeast(0L)
            viewModel.updatePlaybackProgress(position, duration)
            saveTick++
            if (saveTick >= 15) {
                saveTick = 0
                Log.i(VOD_PLAYER_TAG, "Media3 checkpoint: state=${player.playbackState}, playing=${player.isPlaying}, positionMs=$position, durationMs=$duration")
                viewModel.saveProgress(position, duration)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            if (!vlcFallbackActive) {
                viewModel.saveFinalProgress(
                    player.currentPosition.coerceAtLeast(0L),
                    player.duration.coerceAtLeast(0L)
                )
            }
            player.release()
        }
    }

    LaunchedEffect(state.autoNextCountdown, state.nextEpisode?.id, state.seriesId) {
        if (state.autoNextCountdown != 0) return@LaunchedEffect
        val next = state.nextEpisode ?: return@LaunchedEffect
        val currentSeriesId = state.seriesId ?: return@LaunchedEffect
        onPlayEpisode(next, currentSeriesId)
    }

    LaunchedEffect(episodeNavigationVisible, state.nextEpisode?.id, state.previousEpisode?.id) {
        if (!episodeNavigationVisible) return@LaunchedEffect
        delay(120L)
        when {
            state.nextEpisode != null -> runCatching { nextEpisodeFocusRequester.requestFocus() }
            state.previousEpisode != null -> runCatching { previousEpisodeFocusRequester.requestFocus() }
        }
        delay(4_500L)
        episodeNavigationVisible = false
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                val seriesId = state.seriesId
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || seriesId == null) {
                    false
                } else when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_DOWN -> {
                        state.nextEpisode?.let { onPlayEpisode(it, seriesId) } != null
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP -> {
                        state.previousEpisode?.let { onPlayEpisode(it, seriesId) } != null
                    }
                    else -> false
                }
            }
            .pointerInput(state.seriesId, state.nextEpisode?.id, state.previousEpisode?.id) {
                detectTapGestures(onTap = {
                    infoVisible = !infoVisible
                    playbackControlsVisible = !playbackControlsVisible
                    if (state.seriesId != null && (state.nextEpisode != null || state.previousEpisode != null)) {
                        episodeNavigationVisible = infoVisible
                    }
                })
            }
            .pointerInput(Unit) {
                var startX = 0f
                var vertical = 0f
                detectDragGestures(
                    onDragStart = { startX = it.x; vertical = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        vertical += amount.y
                        if (kotlin.math.abs(vertical) > 22f && kotlin.math.abs(amount.y) >= kotlin.math.abs(amount.x)) {
                            val up = vertical < 0
                            if (startX >= size.width / 2f) {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (current + if (up) 1 else -1).coerceIn(0, max), 0)
                                gestureLabel = "Volume " + (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1)) + "%"
                            } else {
                                activity?.let { host ->
                                    val lp = host.window.attributes
                                    val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                    lp.screenBrightness = (current + if (up) 0.04f else -0.04f).coerceIn(0.05f, 1f)
                                    host.window.attributes = lp
                                    gestureLabel = "Luminosité " + (lp.screenBrightness * 100).toInt() + "%"
                                }
                            }
                            vertical = 0f
                        }
                    },
                    onDragEnd = { gestureLabel = null },
                    onDragCancel = { gestureLabel = null }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            castActive -> Text(
                "Lecture sur Chromecast",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall
            )
            state.error != null && !useVlcFallback -> Text(state.error ?: "Erreur", color = MaterialTheme.colorScheme.onBackground)
            state.streamUrl == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            useVlcFallback -> VlcFallbackPlayer(
                url = state.streamUrl.orEmpty(),
                modifier = Modifier.fillMaxSize(),
                startPositionMs = vlcHandoffPositionMs.coerceAtLeast(state.resumePositionMs),
                bufferPreset = playerPrefs.bufferPreset,
                onPlaybackEnded = { viewModel.onPlaybackEnded() },
                onProgress = { position, duration ->
                    viewModel.updatePlaybackProgress(position, duration)
                    // Save roughly every 15 seconds, including after backward seeks.
                    if (shouldSaveVlcProgress(position, lastVlcProgressSaveMs)) {
                        lastVlcProgressSaveMs = position
                        viewModel.saveProgress(position, duration)
                    }
                },
                onFinalProgress = { position, duration -> viewModel.saveFinalProgress(position, duration) },
                onError = { error ->
                    vlcFailedForStream = true
                    useVlcFallback = false
                    viewModel.onPlaybackError(error)
                    state.streamUrl?.let { url ->
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        if (vlcHandoffPositionMs > 0L) player.seekTo(vlcHandoffPositionMs)
                        player.playWhenReady = true
                    }
                }
            )
            else -> AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = true
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            playbackControlsVisible = visibility == View.VISIBLE
                            if (visibility != View.VISIBLE) episodeNavigationVisible = false
                        })
                        keepScreenOn = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { playerView ->
                    val currentSeriesId = state.seriesId
                    val nextEpisode = state.nextEpisode
                    val previousEpisode = state.previousEpisode

                    // Media3 recalculates the built-in previous/next controls from its own
                    // single-item playlist and can disable them again after composition. Hide
                    // those controls and use the Compose episode controls below instead.
                    playerView.findViewById<android.view.View>(Media3UiR.id.exo_next)?.visibility =
                        android.view.View.GONE
                    playerView.findViewById<android.view.View>(Media3UiR.id.exo_prev)?.visibility =
                        android.view.View.GONE

                    playerView.setOnKeyListener { view, keyCode, event ->
                        val seriesId = currentSeriesId ?: return@setOnKeyListener false

                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                if (nextEpisode == null && previousEpisode == null) {
                                    return@setOnKeyListener false
                                }
                                view.clearFocus()
                                episodeNavigationVisible = true
                            }
                            return@setOnKeyListener true
                        }

                        // The Compose screen dispatches media next/previous once.
                        // Letting the embedded PlayerView dispatch them on key-up
                        // would make a single press advance twice when focus moves.
                        false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (playbackControlsVisible) {
        CastRouteButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
        )
        TvFlowSendButton(
            transfer = {
                val key = state.mediaKey
                val kind = key.substringBefore(':').takeIf { it == "vod" || it == "episode" }
                val id = key.substringAfter(':', "").takeIf { it.isNotBlank() }
                if (kind != null && id != null) {
                    PlaybackTransfer(
                        kind = kind,
                        mediaId = id,
                        title = state.title,
                        extension = state.containerExtension,
                        seriesId = state.seriesId,
                        positionMs = player.currentPosition.coerceAtLeast(state.resumePositionMs)
                    )
                } else null
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 80.dp)
                .size(48.dp),
            onSent = { player.pause() }
        )
        }

        val navigationSeriesId = state.seriesId
        if (
            episodeNavigationVisible &&
            navigationSeriesId != null &&
            (state.previousEpisode != null || state.nextEpisode != null) &&
            !castActive
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.previousEpisode?.let { previous ->
                        TextButton(
                            onClick = { onPlayEpisode(previous, navigationSeriesId) },
                            modifier = Modifier.focusRequester(previousEpisodeFocusRequester)
                        ) {
                            Text("‹  Épisode précédent")
                        }
                    }
                    state.nextEpisode?.let { next ->
                        TextButton(
                            onClick = { onPlayEpisode(next, navigationSeriesId) },
                            modifier = Modifier.focusRequester(nextEpisodeFocusRequester)
                        ) {
                            Column {
                                Text("Épisode suivant  ›")
                                Text("S${next.season} · E${next.episodeNumber}  ${next.title}",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (infoVisible) {
            Card(
                modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    state.containerExtension?.let { Text(it.uppercase(), color = MaterialTheme.colorScheme.primary) }
                    state.nextEpisode?.let { Text("À suivre : S${it.season} • E${it.episodeNumber}  ${it.title}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val currentSeriesId = state.seriesId
                    if (currentSeriesId != null && (state.previousEpisode != null || state.nextEpisode != null)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.previousEpisode?.let { previous ->
                                Button(onClick = { onPlayEpisode(previous, currentSeriesId) }) {
                                    Text("⏮ Épisode précédent")
                                }
                            }
                            state.nextEpisode?.let { next ->
                                Button(onClick = { onPlayEpisode(next, currentSeriesId) }) {
                                    Text("Épisode suivant ⏭")
                                }
                            }
                        }
                    }
                    Text("Touchez la vidéo pour masquer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        gestureLabel?.let { label ->
            Card(modifier = Modifier.align(Alignment.Center), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))) {
                Text(label, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }

        val nextEpisode = state.nextEpisode
        val seriesId = state.seriesId
        if (state.offerNextEpisode && nextEpisode != null && seriesId != null) {
            Card(
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Épisode suivant", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "S${nextEpisode.season} • E${nextEpisode.episodeNumber}  ${nextEpisode.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.autoNextCountdown?.let { seconds ->
                        Text(
                            if (seconds > 0) "Lecture automatique dans " + seconds + "s"
                            else "Ouverture de l’épisode suivant…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlayEpisode(nextEpisode, seriesId) }) { Text("Lire maintenant") }
                        TextButton(onClick = viewModel::dismissNextEpisode) { Text("Plus tard") }
                    }
                }
            }
        }
    }
}
