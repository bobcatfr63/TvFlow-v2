@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.tvflow.app.ui.common

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PlaybackLifecycleDeviceTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private fun sample(): File {
        val inst = InstrumentationRegistry.getInstrumentation()
        val file = File(inst.targetContext.cacheDir, "audit-playback.mp4")
        inst.context.assets.open("audit-playback.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    @Test fun media3ReallyPlaysStopsAndResumesWithoutRevivingDisabledEngine() {
        val sample = sample()
        val inst = InstrumentationRegistry.getInstrumentation()
        lateinit var player: ExoPlayer
        lateinit var owner: Owner
        var enabled by mutableStateOf(true)
        val ready = AtomicBoolean(false)
        val firstFrame = AtomicBoolean(false)
        compose.runOnUiThread {
            owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            player = ExoPlayer.Builder(inst.targetContext).build()
            player.volume = 0f
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrame.set(true) }
                override fun onPlaybackStateChanged(state: Int) { ready.set(state == Player.STATE_READY) }
            })
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PlayerLifecycleEffect(player, enabled)
                androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                    androidx.media3.ui.PlayerView(context).apply { this.player = player; useController = false }
                })
            }
        }
        try {
            compose.runOnUiThread { player.setMediaItem(MediaItem.fromUri(Uri.fromFile(sample))); player.prepare(); player.play() }
            compose.waitUntil(15000) { ready.get() }
            compose.waitUntil(15000) { firstFrame.get() }
            Thread.sleep(1500)
            compose.runOnUiThread { assertTrue(player.currentPosition > 500); owner.registry.currentState = Lifecycle.State.CREATED; assertFalse(player.isPlaying); assertEquals(Player.STATE_IDLE, player.playbackState) }
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.waitUntil(15000) { ready.get() }
            compose.runOnUiThread { assertTrue(player.playWhenReady); player.pause(); owner.registry.currentState = Lifecycle.State.CREATED; owner.registry.currentState = Lifecycle.State.RESUMED; assertFalse(player.playWhenReady) }
            compose.runOnUiThread { enabled = false }
            compose.waitForIdle()
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED; owner.registry.currentState = Lifecycle.State.RESUMED; assertEquals(Player.STATE_IDLE, player.playbackState) }
        } finally { compose.runOnUiThread { player.release() }; sample.delete() }
    }

    @Test fun vlcReallyPlaysAndDisposesWithFinalProgress() {
        val sample = sample()
        val ready = AtomicBoolean(false)
        val progress = AtomicLong(0)
        val final = AtomicLong(0)
        var visible by mutableStateOf(true)
        lateinit var owner: Owner
        compose.runOnUiThread { owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED } }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            if (visible) VlcFallbackPlayer(
                url = Uri.fromFile(sample).toString(),
                onReady = { ready.set(true) },
                onProgress = { position, _ -> progress.set(position) },
                onFinalProgress = { position, _ -> final.set(position) }
            )
            }
        }
        try {
            compose.waitUntil(20000) { ready.get() }
            compose.waitUntil(15000) { progress.get() > 500 }
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
            val pausedAt = progress.get()
            Thread.sleep(5500)
            assertTrue("VLC must pause in background", progress.get() - pausedAt < 1000)
            compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
            compose.waitUntil(10000) { progress.get() > pausedAt + 500 }
            compose.runOnUiThread { visible = false }
            compose.waitForIdle()
            assertTrue(final.get() > 500)
        } finally { compose.runOnUiThread { visible = false }; compose.waitForIdle(); sample.delete() }
    }
}
