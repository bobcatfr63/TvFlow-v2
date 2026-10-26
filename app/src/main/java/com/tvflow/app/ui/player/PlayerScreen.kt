@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tvflow.app.ui.player

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.tvflow.app.cast.CastPlaybackEffect
import com.tvflow.app.cast.CastRouteButton
import com.tvflow.app.connect.PlaybackTransfer
import com.tvflow.app.connect.TvFlowSendButton
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.ui.common.PlayerLifecycleEffect
import com.tvflow.app.ui.common.VlcFallbackPlayer
import com.tvflow.app.util.isTelevisionUi
import com.tvflow.app.ui.common.iptvMediaSourceFactory
import com.tvflow.app.ui.common.liveM3u8Fallback
import com.tvflow.app.ui.common.shouldUseVlcFallback
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SidePanel { NONE, CHANNELS, OPTIONS }
private enum class VideoScale(val label: String, val resizeMode: Int) {
    FIT("Ajuster", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Remplir", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

private data class PlayerTrack(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val type: Int,
    val selected: Boolean
)

@Composable
fun PlayerScreen(
    instantTv: Boolean = false,
    onExitToDashboard: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    var instantLogoVisible by remember(instantTv) { mutableStateOf(instantTv) }
    LaunchedEffect(instantTv) {
        if (instantTv) {
            delay(2_000L)
            instantLogoVisible = false
        }
    }
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    var horizontalDrag by remember { mutableStateOf(0f) }
    var verticalDrag by remember { mutableStateOf(0f) }
    var gestureStartX by remember { mutableStateOf(0f) }
    var gestureMode by remember { mutableStateOf("") }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(true) }
    var sidePanel by remember { mutableStateOf(SidePanel.NONE) }
    BackHandler(enabled = instantTv && sidePanel == SidePanel.NONE) {
        onExitToDashboard()
    }
    var overlayEpoch by remember { mutableStateOf(0L) }
    var retryEpoch by remember { mutableIntStateOf(0) }
    var videoScale by remember { mutableStateOf(VideoScale.FIT) }
    var audioTracks by remember { mutableStateOf(emptyList<PlayerTrack>()) }
    var subtitleTracks by remember { mutableStateOf(emptyList<PlayerTrack>()) }
    var subtitlesDisabled by remember { mutableStateOf(false) }
    var automaticRetryCount by remember { mutableIntStateOf(0) }
    var media3ErrorEpoch by remember { mutableIntStateOf(0) }
    var lastMedia3HttpCode by remember { mutableStateOf<Int?>(null) }
    var lastMedia3ErrorName by remember { mutableStateOf<String?>(null) }
    val phoneVlcFirst = remember(context) { !context.isTelevisionUi() }
    var vlcFailedForStream by remember { mutableStateOf(false) }
    var useVlcFallback by remember { mutableStateOf(phoneVlcFirst) }
    var firstVideoFrameRendered by remember { mutableStateOf(false) }
    var videoViewReady by remember { mutableStateOf(false) }
    var playbackReadyForVideo by remember { mutableStateOf(false) }
    var videoWatchdogEpoch by remember { mutableIntStateOf(0) }
    var liveUrlOverride by remember { mutableStateOf<String?>(null) }
    var catchupUrlOverride by remember { mutableStateOf<String?>(null) }
    var formatFallbackTried by remember { mutableStateOf(false) }
    var castActive by remember { mutableStateOf(false) }
    val effectiveStreamUrl = catchupUrlOverride ?: liveUrlOverride ?: state.streamUrl
    val isCatchup = catchupUrlOverride != null

    var autoBufferLevel by remember { mutableIntStateOf(1) }
    val effectiveBufferPreset = if (state.bufferPreset == "AUTO") {
        when (autoBufferLevel) { 0 -> "FAST"; 2 -> "SAFE"; else -> "BALANCED" }
    } else state.bufferPreset
    val player = remember(effectiveBufferPreset) {
        val (minBuffer, maxBuffer, playBuffer, rebuffer) = when (effectiveBufferPreset) {
            "SAFE" -> intArrayOf(8_000, 30_000, 2_000, 3_500)
            "BALANCED" -> intArrayOf(5_000, 20_000, 1_200, 2_000)
            else -> intArrayOf(3_000, 12_000, 750, 1_500)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, playBuffer, rebuffer)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(iptvMediaSourceFactory(context))
            .build()
            .apply { playWhenReady = true }
    }

    CastPlaybackEffect(
        url = effectiveStreamUrl,
        title = state.channel?.name ?: "TVFlow",
        subtitle = state.nowProgram?.title,
        imageUrl = state.channel?.logoUrl,
        isLive = !isCatchup,
        onCastActiveChanged = { castActive = it }
    )

    LaunchedEffect(castActive, player) {
        if (castActive) player.pause()
    }

    LaunchedEffect(state.videoScale) {
        videoScale = VideoScale.entries.firstOrNull { it.name == state.videoScale } ?: VideoScale.FIT
    }

    LaunchedEffect(state.subtitlesEnabled, player) {
        subtitlesDisabled = !state.subtitlesEnabled
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !state.subtitlesEnabled)
            .build()
    }

    PlayerLifecycleEffect(player)

    // Playback diagnostics only; never restart or alter the stream from this sampler.
    LaunchedEffect(player, effectiveStreamUrl, useVlcFallback, castActive) {
        if (effectiveStreamUrl.isNullOrBlank() || useVlcFallback || castActive) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            android.util.Log.i("TVFlowPlaybackHealth",
                "state=${player.playbackState} playing=${player.isPlaying} " +
                "ready=${player.playWhenReady} positionMs=${player.currentPosition} " +
                "bufferedMs=${player.totalBufferedDuration} videoSize=${player.videoSize.width}x${player.videoSize.height} " +
                "firstFrame=$firstVideoFrameRendered")
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    fun refreshTracks() {
        val groups = player.currentTracks.groups
        fun mapType(type: Int): List<PlayerTrack> = groups
            .filter { it.type == type }
            .flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    if (!group.isTrackSupported(index)) return@mapNotNull null
                    val format = group.getTrackFormat(index)
                    val language = format.language?.uppercase(Locale.getDefault())
                    val base = format.label?.takeIf { it.isNotBlank() }
                        ?: language
                        ?: when (type) {
                            C.TRACK_TYPE_AUDIO -> "Audio ${index + 1}"
                            else -> "Sous-titre ${index + 1}"
                        }
                    PlayerTrack(group, index, base, type, group.isTrackSelected(index))
                }
            }
        audioTracks = mapType(C.TRACK_TYPE_AUDIO)
        subtitleTracks = mapType(C.TRACK_TYPE_TEXT)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (catchupUrlOverride != null) {
                    catchupUrlOverride = null
                    viewModel.reportPlayerError("Reprise depuis le début indisponible")
                    return
                }
                var cause: Throwable? = error
                var httpCode: Int? = null
                while (cause != null) {
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cause.responseCode
                        break
                    }
                    cause = cause.cause
                }
                if (
                    httpCode == 403 &&
                    !formatFallbackTried &&
                    liveM3u8Fallback(state.streamUrl.orEmpty()) != null
                ) {
                    formatFallbackTried = true
                    liveUrlOverride = liveM3u8Fallback(state.streamUrl.orEmpty())
                    viewModel.reportPlayerError(null)
                } else {
                    lastMedia3HttpCode = httpCode
                    lastMedia3ErrorName = error.errorCodeName
                    if (state.bufferPreset == "AUTO" && httpCode == null) {
                        autoBufferLevel = (autoBufferLevel + 1).coerceAtMost(2)
                    }
                    media3ErrorEpoch++
                    viewModel.reportPlaybackFailure(httpCode, error.errorCodeName)
                    viewModel.reportPlayerError(
                        httpCode?.let { "HTTP $it" } ?: error.errorCodeName
                    )
                }
            }

            override fun onRenderedFirstFrame() {
                firstVideoFrameRendered = true
                android.util.Log.i("TVFlowVideo", "Media3 rendered first video frame")
                viewModel.reportPlaybackReady()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackReadyForVideo = true
                    android.util.Log.i("TVFlowVideo", "Media3 ready; awaiting first video frame")
                    viewModel.reportPlayerError(null)
                    refreshTracks()
                } else if (playbackState == Player.STATE_ENDED && catchupUrlOverride != null) {
                    catchupUrlOverride = null
                    overlayVisible = true
                    overlayEpoch = System.currentTimeMillis()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracks()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(state.streamUrl) {
        automaticRetryCount = 0
        media3ErrorEpoch = 0
        lastMedia3HttpCode = null
        lastMedia3ErrorName = null
        vlcFailedForStream = false
        useVlcFallback = phoneVlcFirst
        formatFallbackTried = false
        liveUrlOverride = null
        catchupUrlOverride = null
    }

    LaunchedEffect(media3ErrorEpoch) {
        if (media3ErrorEpoch == 0) return@LaunchedEffect
        if (!shouldUseVlcFallback(lastMedia3HttpCode, lastMedia3ErrorName)) return@LaunchedEffect
        if (automaticRetryCount < 2) {
            automaticRetryCount++
            delay(automaticRetryCount * 1_500L)
            retryEpoch++
        } else {
            player.stop()
            if (!vlcFailedForStream && !context.isTelevisionUi()) useVlcFallback = true
            viewModel.reportPlayerError(null)
        }
    }

    LaunchedEffect(useVlcFallback, player) {
        if (useVlcFallback) {
            player.playWhenReady = false
            player.volume = 0f
            player.stop()
            player.clearMediaItems()
        } else {
            player.volume = 1f
        }
    }

    LaunchedEffect(effectiveStreamUrl, retryEpoch, player, useVlcFallback, videoViewReady) {
        if (useVlcFallback || !videoViewReady) return@LaunchedEffect
        effectiveStreamUrl?.let { url ->
            firstVideoFrameRendered = false
            playbackReadyForVideo = false
            videoWatchdogEpoch++
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
            overlayVisible = true
            sidePanel = SidePanel.NONE
            overlayEpoch = System.currentTimeMillis()
        }
    }

    LaunchedEffect(videoWatchdogEpoch, effectiveStreamUrl, useVlcFallback, castActive) {
        if (effectiveStreamUrl.isNullOrBlank() || useVlcFallback || castActive) return@LaunchedEffect
        delay(7_000L)
        if (playbackReadyForVideo && !firstVideoFrameRendered && !useVlcFallback && !castActive) {
            android.util.Log.w("TVFlowVideo", "No first video frame after Media3 READY; retry on safe TV surface")
            if (context.isTelevisionUi()) {
                if (automaticRetryCount < 3) {
                    automaticRetryCount++
                    retryEpoch++
                } else viewModel.reportPlayerError("Vidéo indisponible sur ce flux (audio reçu)")
            } else {
                player.stop()
                useVlcFallback = true
                viewModel.reportPlayerError(null)
            }
        }
    }

    LaunchedEffect(overlayEpoch, sidePanel) {
        if (overlayEpoch == 0L || sidePanel != SidePanel.NONE) return@LaunchedEffect
        delay(4_500)
        overlayVisible = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun restartCurrentProgram() {
        val url = state.restartUrl ?: return
        useVlcFallback = false
        liveUrlOverride = null
        catchupUrlOverride = url
        overlayVisible = true
        sidePanel = SidePanel.NONE
        overlayEpoch = System.currentTimeMillis()
        viewModel.reportPlayerError(null)
    }

    fun returnToLive() {
        catchupUrlOverride = null
        useVlcFallback = false
        overlayVisible = true
        sidePanel = SidePanel.NONE
        overlayEpoch = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(sidePanel, state.channel?.streamId) {
                if (sidePanel == SidePanel.NONE) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            horizontalDrag = 0f
                            verticalDrag = 0f
                            gestureStartX = offset.x
                            gestureMode = ""
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            horizontalDrag += amount.x
                            verticalDrag += amount.y
                            if (gestureMode.isEmpty() && kotlin.math.abs(horizontalDrag) + kotlin.math.abs(verticalDrag) > 18f) {
                                gestureMode = if (kotlin.math.abs(horizontalDrag) > kotlin.math.abs(verticalDrag)) "H" else "V"
                            }
                            if (gestureMode == "V") {
                                val up = -amount.y
                                if (gestureStartX >= size.width / 2f) {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val step = if (up > 0) 1 else -1
                                    if (kotlin.math.abs(verticalDrag) > 24f) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (current + step).coerceIn(0, max), 0)
                                        verticalDrag = 0f
                                    }
                                    gestureLabel = "Volume " + (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1)) + "%"
                                } else {
                                    activity?.let { host ->
                                        val lp = host.window.attributes
                                        val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                        if (kotlin.math.abs(verticalDrag) > 18f) {
                                            lp.screenBrightness = (current + if (up > 0) 0.04f else -0.04f).coerceIn(0.05f, 1f)
                                            host.window.attributes = lp
                                            verticalDrag = 0f
                                        }
                                        gestureLabel = "Luminosité " + ((host.window.attributes.screenBrightness.coerceAtLeast(0f)) * 100).toInt() + "%"
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (gestureMode == "H") {
                                when {
                                    horizontalDrag <= -swipeThresholdPx -> viewModel.nextChannel()
                                    horizontalDrag >= swipeThresholdPx -> viewModel.previousChannel()
                                }
                            }
                            gestureLabel = null
                            gestureMode = ""
                        },
                        onDragCancel = { gestureLabel = null; gestureMode = "" }
                    )
                }
            }
            .pointerInput(sidePanel, overlayVisible) {
                if (sidePanel == SidePanel.NONE) {
                    detectTapGestures {
                        overlayVisible = !overlayVisible
                        overlayEpoch = if (overlayVisible) System.currentTimeMillis() else 0L
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val isLongPress = native.repeatCount > 0
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (sidePanel == SidePanel.NONE) viewModel.previousChannel() else return@onPreviewKeyEvent false
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (sidePanel == SidePanel.NONE) viewModel.nextChannel() else return@onPreviewKeyEvent false
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> {
                        sidePanel = if (sidePanel == SidePanel.CHANNELS) SidePanel.NONE else SidePanel.CHANNELS
                        overlayVisible = true
                        overlayEpoch = System.currentTimeMillis()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        sidePanel = if (sidePanel == SidePanel.OPTIONS) SidePanel.NONE else SidePanel.OPTIONS
                        overlayVisible = true
                        overlayEpoch = System.currentTimeMillis()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (isLongPress) {
                            sidePanel = SidePanel.OPTIONS
                            overlayVisible = true
                        } else if (sidePanel == SidePanel.NONE) {
                            overlayVisible = !overlayVisible
                        } else {
                            return@onPreviewKeyEvent false
                        }
                        overlayEpoch = if (overlayVisible) System.currentTimeMillis() else 0L
                        true
                    }
                    KeyEvent.KEYCODE_INFO -> {
                        overlayVisible = !overlayVisible
                        overlayEpoch = if (overlayVisible) System.currentTimeMillis() else 0L
                        true
                    }
                    KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_MEDIA_RECORD -> {
                        viewModel.toggleFavorite()
                        overlayVisible = true
                        overlayEpoch = System.currentTimeMillis()
                        true
                    }
                    KeyEvent.KEYCODE_0 -> {
                        viewModel.returnToPreviousChannel()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (state.restartUrl != null) restartCurrentProgram() else return@onPreviewKeyEvent false
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (isCatchup) returnToLive() else return@onPreviewKeyEvent false
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (sidePanel != SidePanel.NONE) {
                            sidePanel = SidePanel.NONE
                            true
                        } else false
                    }
                    else -> false
                }
            }
    ) {
        if (castActive) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text(
                    "Lecture sur Chromecast",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else if (useVlcFallback && !effectiveStreamUrl.isNullOrBlank()) {
            VlcFallbackPlayer(
                url = effectiveStreamUrl,
                modifier = Modifier.fillMaxSize(),
                bufferPreset = effectiveBufferPreset,
                onReady = viewModel::reportPlaybackReady,
                onError = {
                    vlcFailedForStream = true
                    useVlcFallback = false
                    viewModel.reportPlaybackFailure(null, it)
                    viewModel.reportPlayerError(it)
                }
            )
        } else {
            AndroidView(
                factory = {
                    (if (context.isTelevisionUi()) android.view.LayoutInflater.from(it).inflate(com.tvflow.app.R.layout.tvflow_texture_player, null, false) as PlayerView else PlayerView(it)).apply {
                        this.player = player
                        videoViewReady = true
                        useController = false
                        keepScreenOn = true
                        resizeMode = videoScale.resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                    view.resizeMode = videoScale.resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        CastRouteButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
        )
        TvFlowSendButton(
            transfer = {
                state.channel?.let { channel ->
                    PlaybackTransfer(
                        kind = "live",
                        mediaId = channel.streamId.toString(),
                        title = channel.name,
                        categoryId = channel.categoryId
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 80.dp)
                .size(48.dp),
            onSent = { player.pause() }
        )

        AnimatedVisibility(
            visible = sidePanel == SidePanel.CHANNELS,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            MiniGuide(
                channels = state.channels,
                selectedStreamId = state.channel?.streamId,
                onSelect = {
                    viewModel.selectChannel(it)
                    sidePanel = SidePanel.NONE
                }
            )
        }

        AnimatedVisibility(
            visible = sidePanel == SidePanel.OPTIONS,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlayerOptionsPanel(
                scale = videoScale,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                subtitlesDisabled = subtitlesDisabled,
                hasPreviousChannel = state.previousStreamId != null,
                canRestartProgram = state.restartUrl != null,
                isCatchup = isCatchup,
                onScaleChange = {
                    videoScale = it
                    viewModel.saveVideoScale(it.name)
                },
                onAudioTrack = { track -> selectTrack(player, track) },
                onSubtitleTrack = { track ->
                    subtitlesDisabled = false
                    viewModel.saveSubtitlesEnabled(true)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
                        .build()
                },
                onDisableSubtitles = {
                    subtitlesDisabled = true
                    viewModel.saveSubtitlesEnabled(false)
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                },
                onFavorite = viewModel::toggleFavorite,
                onRestartProgram = ::restartCurrentProgram,
                onReturnToLive = ::returnToLive,
                onPreviousChannel = {
                    viewModel.returnToPreviousChannel()
                    sidePanel = SidePanel.NONE
                }
            )
        }

        gestureLabel?.let { label ->
            Box(
                Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(14.dp)).padding(horizontal = 24.dp, vertical = 16.dp)
            ) { Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium) }
        }

        if (instantLogoVisible) {
            Text(
                text = "TVFlow",
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerOverlay(
                state = state,
                isCatchup = isCatchup,
                onRestartProgram = ::restartCurrentProgram,
                onReturnToLive = ::returnToLive,
                onRetry = { retryEpoch++ }
            )
        }
    }
}

private fun selectTrack(player: ExoPlayer, track: PlayerTrack) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex))
        .build()
}

@Composable
private fun MiniGuide(
    channels: List<LiveChannel>,
    selectedStreamId: Int?,
    onSelect: (Int) -> Unit
) {
    val selectedIndex = channels.indexOfFirst { it.streamId == selectedStreamId }.coerceAtLeast(0)
    val from = (selectedIndex - 5).coerceAtLeast(0)
    val to = (selectedIndex + 7).coerceAtMost(channels.size)
    val visibleChannels = channels.subList(from, to)

    Column(
        modifier = Modifier
            .width(390.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(18.dp)
    ) {
        Text("Chaînes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visibleChannels, key = { it.streamId }) { channel ->
                FocusActionRow(
                    selected = channel.streamId == selectedStreamId,
                    onClick = { onSelect(channel.streamId) }
                ) {
                    AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.width(46.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayerOptionsPanel(
    scale: VideoScale,
    audioTracks: List<PlayerTrack>,
    subtitleTracks: List<PlayerTrack>,
    subtitlesDisabled: Boolean,
    hasPreviousChannel: Boolean,
    canRestartProgram: Boolean,
    isCatchup: Boolean,
    onScaleChange: (VideoScale) -> Unit,
    onAudioTrack: (PlayerTrack) -> Unit,
    onSubtitleTrack: (PlayerTrack) -> Unit,
    onDisableSubtitles: () -> Unit,
    onFavorite: () -> Unit,
    onRestartProgram: () -> Unit,
    onReturnToLive: () -> Unit,
    onPreviousChannel: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(430.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Réglages lecteur", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        Text("Image", style = MaterialTheme.typography.titleMedium)
        VideoScale.entries.forEach { item ->
            FocusActionRow(selected = item == scale, onClick = { onScaleChange(item) }) {
                Text(if (item == scale) "● ${item.label}" else "○ ${item.label}")
            }
        }

        if (audioTracks.isNotEmpty()) {
            Text("Audio", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            audioTracks.take(6).forEach { track ->
                FocusActionRow(selected = track.selected, onClick = { onAudioTrack(track) }) {
                    Text(if (track.selected) "● ${track.label}" else "○ ${track.label}")
                }
            }
        }

        Text("Sous-titres", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        FocusActionRow(selected = subtitlesDisabled, onClick = onDisableSubtitles) {
            Text(if (subtitlesDisabled) "● Désactivés" else "○ Désactivés")
        }
        subtitleTracks.take(5).forEach { track ->
            FocusActionRow(selected = !subtitlesDisabled && track.selected, onClick = { onSubtitleTrack(track) }) {
                Text(if (!subtitlesDisabled && track.selected) "● ${track.label}" else "○ ${track.label}")
            }
        }

        Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        if (isCatchup) {
            FocusActionRow(onClick = onReturnToLive) { Text("● Revenir au direct") }
        } else if (canRestartProgram) {
            FocusActionRow(onClick = onRestartProgram) { Text("↺ Recommencer le programme") }
        }
        FocusActionRow(onClick = onFavorite) { Text("★ Ajouter / retirer des favoris") }
        if (hasPreviousChannel) {
            FocusActionRow(onClick = onPreviousChannel) { Text("↶ Revenir à la chaîne précédente") }
        }
    }
}

@Composable
private fun FocusActionRow(
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primaryContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
                RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun PlayerOverlay(
    state: PlayerUiState,
    isCatchup: Boolean,
    onRestartProgram: () -> Unit,
    onReturnToLive: () -> Unit,
    onRetry: () -> Unit
) {
    val current = state.nowProgram
    val nowSeconds = System.currentTimeMillis() / 1000L
    val progress = if (current != null && current.stopTimestamp > current.startTimestamp) {
        ((nowSeconds - current.startTimestamp).toFloat() / (current.stopTimestamp - current.startTimestamp).toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 32.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.channelIndex >= 0) "${state.channelIndex + 1}" else "–",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(54.dp)
            )
            AsyncImage(model = state.channel?.logoUrl, contentDescription = null, modifier = Modifier.width(72.dp))
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(state.channel?.name ?: "Chargement…", style = MaterialTheme.typography.headlineSmall)
                if (current != null) {
                    Text(
                        "${formatTime(current.startTimestamp)} - ${formatTime(current.stopTimestamp)}  ${current.title}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(if (state.epgLoading) "Chargement du programme…" else "Programme indisponible")
                }
                state.nextProgram?.let {
                    Text(
                        "Ensuite ${formatTime(it.startTimestamp)}  ${it.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                state.playerError?.let { error ->
                    Text(
                        "Flux indisponible ($error) • Réessayer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onRetry)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isCatchup) {
                    Text(
                        "● Revenir au direct",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onReturnToLive)
                    )
                } else if (state.restartUrl != null) {
                    Text(
                        "↺ Recommencer",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onRestartProgram)
                    )
                }
                Text(if (state.channel?.isFavorite == true) "★ Favori" else "☆ Favori")
                Text(if (state.channelCount > 0) "${state.channelIndex + 1}/${state.channelCount}" else "")
                Text("↑↓ Zapper   ← Chaînes   → Réglages", style = MaterialTheme.typography.bodySmall)
                Text("0 Chaîne précédente   OK Infos", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (current != null) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatTime(timestampSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
