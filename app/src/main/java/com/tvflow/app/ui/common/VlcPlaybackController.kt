package com.tvflow.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class VlcTrack(val id: Int, val label: String)

internal data class VlcPlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val playRequested: Boolean = true,
    val canSeek: Boolean = false,
    val audioTracks: List<VlcTrack> = emptyList(),
    val subtitleTracks: List<VlcTrack> = emptyList(),
    val audioTrackId: Int = -1,
    val subtitleTrackId: Int = -1,
    val commandError: String? = null
)

/** JNI access stays on the main thread; a fake port exercises the same command path in tests. */
internal interface VlcPlaybackPort {
    val positionMs: Long
    val durationMs: Long
    val playing: Boolean
    val seekable: Boolean
    val audioTracks: List<VlcTrack>
    val subtitleTracks: List<VlcTrack>
    val audioTrackId: Int
    val subtitleTrackId: Int
    fun play()
    fun pause()
    fun seekTo(positionMs: Long): Boolean
    fun selectAudio(id: Int): Boolean
    fun selectSubtitle(id: Int): Boolean
}

internal fun boundedVodSeek(positionMs: Long, durationMs: Long): Long =
    positionMs.coerceAtLeast(0).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }

internal fun vodTimeLabel(timeMs: Long): String {
    val seconds = timeMs.coerceAtLeast(0) / 1_000
    val s = (seconds % 60).toString().padStart(2, '0')
    val m = ((seconds / 60) % 60).toString().padStart(2, '0')
    return if (seconds >= 3_600) "${seconds / 3_600}:$m:$s" else "$m:$s"
}

internal class VlcPlaybackController {
    private val mutableState = MutableStateFlow(VlcPlaybackState())
    val state: StateFlow<VlcPlaybackState> = mutableState.asStateFlow()
    private var port: VlcPlaybackPort? = null
    private var foreground = false
    private var mediaLoaded = false
    private var playRequested = true
    private var pendingResumeMs: Long? = null

    fun bind(value: VlcPlaybackPort, inForeground: Boolean) {
        port = value
        foreground = inForeground
        mediaLoaded = false
        playRequested = true
        pendingResumeMs = null
        mutableState.value = VlcPlaybackState()
    }

    fun unbind(value: VlcPlaybackPort) {
        if (port !== value) return
        port = null
        mediaLoaded = false
        pendingResumeMs = null
        mutableState.value = mutableState.value.copy(playing = false, canSeek = false)
    }

    fun onMediaLoaded(startPositionMs: Long) {
        mediaLoaded = true
        playRequested = true
        pendingResumeMs = startPositionMs.takeIf { it > 0 }
        applyPlaybackIntent()
        refresh()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        applyPlaybackIntent()
        refresh()
    }

    fun setPlaying(value: Boolean) {
        playRequested = value
        applyPlaybackIntent()
        refresh()
    }

    fun togglePlaying() = setPlaying(!playRequested)

    private fun applyPlaybackIntent() {
        val engine = port ?: return
        if (!mediaLoaded) return
        runCatching {
            if (foreground && playRequested) engine.play() else engine.pause()
        }.onFailure { commandError("Commande de lecture indisponible.") }
    }

    fun refresh() {
        val engine = port ?: return
        runCatching {
            val duration = engine.durationMs.coerceAtLeast(0)
            val pending = pendingResumeMs
            if (mediaLoaded && pending != null && engine.seekable &&
                engine.seekTo(boundedVodSeek(pending, duration))) pendingResumeMs = null
            mutableState.value = mutableState.value.copy(
                positionMs = engine.positionMs.coerceAtLeast(0), durationMs = duration,
                playing = engine.playing, playRequested = playRequested,
                canSeek = mediaLoaded && engine.seekable && duration > 0,
                audioTracks = engine.audioTracks, subtitleTracks = engine.subtitleTracks,
                audioTrackId = engine.audioTrackId, subtitleTrackId = engine.subtitleTrackId
            )
        }.onFailure { commandError("Informations de lecture indisponibles.") }
    }

    fun seekTo(positionMs: Long) {
        val engine = port ?: return
        if (!state.value.canSeek) return
        pendingResumeMs = null
        val target = boundedVodSeek(positionMs, state.value.durationMs)
        val accepted = runCatching { engine.seekTo(target) }.getOrDefault(false)
        if (accepted) mutableState.value = state.value.copy(positionMs = target, commandError = null)
        else commandError("Déplacement indisponible sur ce flux.")
    }

    fun seekBy(deltaMs: Long) {
        val current = boundedVodSeek(state.value.positionMs, state.value.durationMs)
        val target = runCatching { Math.addExact(current, deltaMs) }
            .getOrElse { if (deltaMs > 0) Long.MAX_VALUE else 0L }
        seekTo(target)
    }

    fun selectAudio(id: Int) {
        val engine = port ?: return
        if (state.value.audioTracks.none { it.id == id }) return
        if (runCatching { engine.selectAudio(id) }.getOrDefault(false)) {
            mutableState.value = state.value.copy(commandError = null)
            refresh()
        } else commandError("Cette piste audio ne peut pas être sélectionnée.")
    }

    fun selectSubtitle(id: Int) {
        val engine = port ?: return
        if (id != -1 && state.value.subtitleTracks.none { it.id == id }) return
        if (runCatching { engine.selectSubtitle(id) }.getOrDefault(false)) {
            mutableState.value = state.value.copy(commandError = null)
            refresh()
        } else commandError("Ces sous-titres ne peuvent pas être sélectionnés.")
    }

    fun clearError() { mutableState.value = state.value.copy(commandError = null) }
    private fun commandError(message: String) { mutableState.value = state.value.copy(commandError = message) }
}
