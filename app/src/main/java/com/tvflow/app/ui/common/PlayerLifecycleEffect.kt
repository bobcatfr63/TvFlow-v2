package com.tvflow.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player

/**
 * Keeps Media3 playback aligned with the Activity lifecycle.
 * A manually paused player stays paused when the app returns to foreground.
 */
@Composable
fun PlayerLifecycleEffect(player: Player) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumeState = remember(player) { booleanArrayOf(false) }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Incoming calls can trigger ON_PAUSE before the Activity stops.
                    resumeState[0] = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (resumeState[0] && player.mediaItemCount > 0) {
                        resumeState[0] = false
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
