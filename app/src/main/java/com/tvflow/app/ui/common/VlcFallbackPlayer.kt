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
fun VlcFallbackPlayer(
    url: String,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    bufferPreset: String = "FAST",
    muted: Boolean = false,
    onPlaybackEnded: () -> Unit = {},
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    onFinalProgress: (Long, Long) -> Unit = { _, _ -> },
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val networkCacheMs = when (bufferPreset) {
        "SAFE" -> 3_500
        "BALANCED" -> 2_000
        else -> 750
    }
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

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> onReady()
                MediaPlayer.Event.EndReached -> onPlaybackEnded()
                MediaPlayer.Event.EncounteredError -> onError("Lecture libVLC impossible")
            }
        }
        onDispose {
            runCatching {
                val position = mediaPlayer.time.coerceAtLeast(0L)
                if (position > 0L) onFinalProgress(position, mediaPlayer.length.coerceAtLeast(0L))
            }
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
                    mediaPlayer.attachViews(layout, null, false, false)
                    attached = true
                }
            },
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
        mediaPlayer.play()
        if (startPositionMs > 0L) {
            delay(400L)
            runCatching { mediaPlayer.setTime(startPositionMs, true) }
        }
    }

    LaunchedEffect(mediaPlayer, url, attached) {
        if (!attached) return@LaunchedEffect
        while (true) {
            delay(5_000L)
            onProgress(
                mediaPlayer.time.coerceAtLeast(0L),
                mediaPlayer.length.coerceAtLeast(0L)
            )
        }
    }
}
