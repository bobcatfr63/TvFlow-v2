package com.tvflow.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player

/** Suspend resources when hidden; never revive Media3 behind libVLC or Cast. */
@Composable
fun PlayerLifecycleEffect(player: Player, enabled: Boolean = true) {
    val owner = LocalLifecycleOwner.current
    val playbackEnabled by rememberUpdatedState(enabled)
    DisposableEffect(owner, player) {
        var resume = false
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    resume = playbackEnabled && player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_STOP -> {
                    stopped = player.mediaItemCount > 0
                    player.stop()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (playbackEnabled && player.mediaItemCount > 0) {
                        if (stopped) player.prepare()
                        if (resume) player.play()
                    }
                    stopped = false
                    resume = false
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
