package com.tvflow.app.ui.common

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

internal fun shouldUseVlcFallback(httpCode: Int?, errorName: String?): Boolean {
    if (httpCode != null && httpCode in 400..499) return false
    return !errorName.isNullOrBlank()
}

@Composable
fun VlcFallbackPlayer(
    url: String,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    onPlaybackEnded: () -> Unit = {},
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    onReady: () -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val libVlc = remember {
        LibVLC(
            context,
            arrayListOf(
                "--network-caching=1500",
                "--http-reconnect",
                "--no-drop-late-frames",
                "--no-skip-frames"
            )
        )
    }
    val mediaPlayer = remember { MediaPlayer(libVlc) }
    var attached by remember { mutableStateOf(false) }

    DisposableEffect(mediaPlayer) {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> onReady()
                MediaPlayer.Event.EndReached -> onPlaybackEnded()
                MediaPlayer.Event.EncounteredError -> onError("Lecture libVLC impossible")
            }
        }
        onDispose {
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.detachViews() }
            mediaPlayer.release()
            libVlc.release()
        }
    }

    AndroidView(
        factory = { hostContext ->
            VLCVideoLayout(hostContext).also { layout ->
                mediaPlayer.attachViews(layout, null, false, false)
                attached = true
            }
        },
        modifier = modifier.fillMaxSize()
    )

    LaunchedEffect(url, attached) {
        if (!attached) return@LaunchedEffect
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1500")
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
