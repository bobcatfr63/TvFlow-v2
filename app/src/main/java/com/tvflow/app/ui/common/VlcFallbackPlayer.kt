package com.tvflow.app.ui.common

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvflow.app.util.isTelevisionUi
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

internal fun shouldSaveVlcProgress(positionMs: Long, lastSavedPositionMs: Long): Boolean =
    positionMs > 0L && (lastSavedPositionMs == 0L ||
        kotlin.math.abs(positionMs - lastSavedPositionMs) >= 15_000L)

internal fun shouldUseVlcFallback(httpCode: Int?, errorName: String?): Boolean {
    if (httpCode != null && httpCode in 400..499) return false
    return !errorName.isNullOrBlank()
}

@Composable
internal fun VlcFallbackPlayer(
    url: String,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    bufferPreset: String = "FAST",
    muted: Boolean = false,
    controller: VlcPlaybackController? = null,
    onPlaybackEnded: () -> Unit = {},
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    onFinalProgress: (Long, Long) -> Unit = { _, _ -> },
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentReady by androidx.compose.runtime.rememberUpdatedState(onReady)
    val currentEnded by androidx.compose.runtime.rememberUpdatedState(onPlaybackEnded)
    val currentError by androidx.compose.runtime.rememberUpdatedState(onError)
    val currentFinalProgress by androidx.compose.runtime.rememberUpdatedState(onFinalProgress)
    val currentProgress by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val context = LocalContext.current
    val networkCacheMs = AdaptiveBufferPolicy.vlcCacheMs(bufferPreset)
    val libVlc = remember(networkCacheMs) {
        LibVLC(
            context,
            arrayListOf(
                "--network-caching=$networkCacheMs",
                "--http-reconnect",
                "--no-drop-late-frames",
                "--no-skip-frames"
            )
        )
    }
    val mediaPlayer = remember(libVlc) { MediaPlayer(libVlc) }
    LaunchedEffect(mediaPlayer, muted) { mediaPlayer.volume = if (muted) 0 else 100 }
    var attached by remember(mediaPlayer) { mutableStateOf(false) }

    val playbackController = controller ?: remember(mediaPlayer) { VlcPlaybackController() }
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
    val playbackPort = remember(mediaPlayer) {
        object : VlcPlaybackPort {
            override val positionMs get() = mediaPlayer.time
            override val durationMs get() = mediaPlayer.length
            override val playing get() = mediaPlayer.isPlaying
            override val seekable get() = mediaPlayer.isSeekable
            override val audioTracks get() = mediaPlayer.audioTracks.orEmpty().map {
                VlcTrack(it.id, it.name.orEmpty().ifBlank { "Piste ${it.id}" })
            }.distinctBy { it.id }
            override val subtitleTracks get() = mediaPlayer.spuTracks.orEmpty().map {
                VlcTrack(it.id, it.name.orEmpty().ifBlank { "Sous-titres ${it.id}" })
            }.distinctBy { it.id }
            override val audioTrackId get() = mediaPlayer.audioTrack
            override val subtitleTrackId get() = mediaPlayer.spuTrack
            override fun play() { mediaPlayer.play() }
            override fun pause() { mediaPlayer.pause() }
            // Accurate seek: fast=true snaps back to an earlier keyframe on some VOD files.
            override fun seekTo(positionMs: Long) = mediaPlayer.setTime(positionMs) >= 0
            override fun selectAudio(id: Int) = mediaPlayer.setAudioTrack(id)
            override fun selectSubtitle(id: Int) = mediaPlayer.setSpuTrack(id)
        }
    }
    DisposableEffect(lifecycleOwner, mediaPlayer, playbackController) {
        playbackController.bind(playbackPort,
            lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> playbackController.setForeground(false)
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> playbackController.setForeground(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playbackController.unbind(playbackPort)
        }
    }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> { playbackController.refresh(); currentReady() }
                MediaPlayer.Event.EndReached -> currentEnded()
                MediaPlayer.Event.EncounteredError -> currentError("Lecture libVLC impossible")
            }
        }
        onDispose {
            runCatching {
                val position = mediaPlayer.time.coerceAtLeast(0L)
                if (position > 0L) currentFinalProgress(position, mediaPlayer.length.coerceAtLeast(0L))
            }
            playbackController.unbind(playbackPort)
            mediaPlayer.setEventListener(null)
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.detachViews() }
            mediaPlayer.release()
            libVlc.release()
        }
    }

    key(mediaPlayer) {
        AndroidView(
            factory = { hostContext ->
                VLCVideoLayout(hostContext).also { layout ->
                    // TextureView keeps mobile Compose controls in the same composition plane.
                    // TV retains SurfaceView for hardware overlay efficiency.
                    mediaPlayer.attachViews(layout, null, true, !hostContext.isTelevisionUi())
                    attached = true
                }
            },
            update = { it.keepScreenOn = playbackState.playing },
            modifier = modifier.fillMaxSize()
        )
    }

    LaunchedEffect(url, attached, mediaPlayer, networkCacheMs) {
        if (!attached) return@LaunchedEffect
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=$networkCacheMs")
            addOption(":http-reconnect")
        }
        mediaPlayer.media = media
        media.release()
        playbackController.onMediaLoaded(startPositionMs)
    }

    LaunchedEffect(mediaPlayer, url, attached) {
        if (!attached) return@LaunchedEffect
        var ticks = 0
        while (true) {
            delay(500L)
            playbackController.refresh()
            val snapshot = playbackController.state.value
            currentProgress(snapshot.positionMs, snapshot.durationMs)
            if (++ticks % 10 == 0 && com.tvflow.app.BuildConfig.DEBUG) {
                android.util.Log.i("TVFlowVlc", "playing=${snapshot.playing} requested=${snapshot.playRequested} positionMs=${snapshot.positionMs} durationMs=${snapshot.durationMs} seekable=${snapshot.canSeek}")
            }
        }
    }
}
