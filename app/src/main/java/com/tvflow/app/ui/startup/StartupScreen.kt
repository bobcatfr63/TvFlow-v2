package com.tvflow.app.ui.startup

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tvflow.app.R
import com.tvflow.app.util.isTelevisionUi
import kotlinx.coroutines.delay

private const val INTRO_FAILSAFE_MS = 4_500L

@Composable
fun StartupScreen(
    onLoggedIn: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: StartupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    // On low-memory Android TV boxes, initializing Media3 during the first frame
    // blocks Compose and adds several seconds before the dashboard can appear.
    // Returning TV users go straight to their catalog; keep the cinematic intro on mobile.
    if (context.isTelevisionUi()) {
        LaunchedEffect(state) {
            when (state) {
                StartupState.LoggedIn -> onLoggedIn()
                StartupState.LoggedOut -> onLoggedOut()
                StartupState.Loading -> Unit
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    var introFinished by rememberSaveable { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        val decoderStart = SystemClock.uptimeMillis()
        Log.i("TVFlowStartup", "Startup player creation begins")
        ExoPlayer.Builder(context).build().apply {
            val introUri = "android.resource://${context.packageName}/${R.raw.tvflow_intro}"
            setMediaItem(MediaItem.fromUri(introUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            playWhenReady = true
        }.also {
            Log.i("TVFlowStartup", "Startup player created in ${SystemClock.uptimeMillis() - decoderStart}ms")
        }
    }

    // Render the initial Compose frame before preparing the video decoder.
    LaunchedEffect(exoPlayer) {
        delay(250L)
        exoPlayer.prepare()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    introFinished = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                introFinished = true
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        delay(INTRO_FAILSAFE_MS)
        introFinished = true
    }

    LaunchedEffect(state, introFinished) {
        if (!introFinished) return@LaunchedEffect

        when (state) {
            StartupState.LoggedIn -> onLoggedIn()
            StartupState.LoggedOut -> onLoggedOut()
            StartupState.Loading -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    player = exoPlayer
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            }
        )
    }
}
