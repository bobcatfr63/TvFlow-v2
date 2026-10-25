@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tvflow.app.ui.startup

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.currentStateAsState
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

private const val INTRO_FAILSAFE_MS = 15_000L

@Composable
fun StartupScreen(
    onLoggedIn: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: StartupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    val state by viewModel.state.collectAsState()
    val catalogStatus by viewModel.catalogStatus.collectAsState()
    var skippedLoading by rememberSaveable { mutableStateOf(false) }
    var introFinished by rememberSaveable { mutableStateOf(false) }

    var exoPlayer by remember(context) { mutableStateOf<ExoPlayer?>(null) }

    // Compose the first black frame before the potentially expensive Media3 initialization.
    // ExoPlayer must still be created and released on the main thread.
    LaunchedEffect(context, resumed) {
        delay(250L)
        if (introFinished || exoPlayer != null || !resumed) return@LaunchedEffect
        val decoderStart = SystemClock.uptimeMillis()
        Log.i("TVFlowStartup", "Startup player creation begins")
        val player = ExoPlayer.Builder(context).build().apply {
            val introUri = "android.resource://${context.packageName}/${R.raw.tvflow_intro}"
            setMediaItem(MediaItem.fromUri(introUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            playWhenReady = true
        }
        exoPlayer = player
        Log.i("TVFlowStartup", "Startup player created in ${SystemClock.uptimeMillis() - decoderStart}ms")
    }

    exoPlayer?.let { com.tvflow.app.ui.common.PlayerLifecycleEffect(it) }

    LaunchedEffect(exoPlayer) {
        exoPlayer?.prepare()
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) return@DisposableEffect onDispose { }

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

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        delay(INTRO_FAILSAFE_MS)
        introFinished = true
    }

    LaunchedEffect(state, introFinished, catalogStatus, skippedLoading) {
        if (!introFinished) return@LaunchedEffect

        when (state) {
            StartupState.LoggedIn -> if (shouldOpenCatalog(introFinished, true, catalogStatus, skippedLoading)) onLoggedIn()
            StartupState.LoggedOut -> onLoggedOut()
            StartupState.Loading -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (introFinished && state == StartupState.LoggedIn && !skippedLoading && catalogStatus != CatalogStatus.Ready && catalogStatus != CatalogStatus.Failed) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("TVFlow • Préparation du catalogue", color = Color.White)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                Text(
                    if (catalogStatus == CatalogStatus.Syncing) "Récupération des chaînes TV depuis votre serveur…" else "Vérification du catalogue local…",
                    color = Color.White,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text("Les films et les séries restent accessibles dès leur disponibilité.", color = Color.LightGray, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { skippedLoading = true }, modifier = Modifier.padding(top = 20.dp)) {
                    Text("Accéder à TVFlow")
                }
            }
        } else if (!introFinished) AndroidView(
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
