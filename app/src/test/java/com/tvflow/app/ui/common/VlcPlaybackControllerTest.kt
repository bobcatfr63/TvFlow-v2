package com.tvflow.app.ui.common

import org.junit.Assert.*
import org.junit.Test

class VlcPlaybackControllerTest {
    private class Engine : VlcPlaybackPort {
        override var positionMs = 0L
        override var durationMs = 120_000L
        override var playing = false
        override var seekable = true
        override val audioTracks = listOf(VlcTrack(1, "Français"), VlcTrack(2, "English"))
        override val subtitleTracks = listOf(VlcTrack(4, "Français"))
        override var audioTrackId = 1
        override var subtitleTrackId = -1
        var playCalls = 0
        var pauseCalls = 0
        var seekCalls = 0
        var acceptTracks = true
        override fun play() { playCalls++; playing = true }
        override fun pause() { pauseCalls++; playing = false }
        override fun seekTo(positionMs: Long): Boolean { seekCalls++; this.positionMs = positionMs; return true }
        override fun selectAudio(id: Int): Boolean { if (acceptTracks) audioTrackId = id; return acceptTracks }
        override fun selectSubtitle(id: Int): Boolean { if (acceptTracks) subtitleTrackId = id; return acceptTracks }
    }
    private fun ready(engine: Engine = Engine()): Pair<VlcPlaybackController, Engine> {
        val controller = VlcPlaybackController()
        controller.bind(engine, true)
        controller.onMediaLoaded(0)
        return controller to engine
    }

    @Test fun commandsOperateOnTheBoundEngineAndPosition() {
        val (c, e) = ready()
        e.positionMs = 45_000
        c.refresh()
        assertEquals(45_000L, c.state.value.positionMs)
        c.togglePlaying()
        assertFalse(e.playing)
        c.togglePlaying()
        assertTrue(e.playing)
        c.seekBy(10_000)
        assertEquals(55_000L, e.positionMs)
        c.seekBy(-10_000)
        assertEquals(45_000L, e.positionMs)
    }
    @Test fun seeksAreBoundedAndDisabledUntilTheStreamAllowsThem() {
        val (c, e) = ready()
        c.seekTo(-40)
        assertEquals(0L, e.positionMs)
        c.seekTo(Long.MAX_VALUE)
        assertEquals(e.durationMs, e.positionMs)
        e.seekable = false
        c.refresh()
        val calls = e.seekCalls
        c.seekTo(50_000)
        assertEquals(calls, e.seekCalls)
    }
    @Test fun resumeWaitsForSeekabilityRatherThanAFixedDelay() {
        val e = Engine().also { it.seekable = false }
        val c = VlcPlaybackController()
        c.bind(e, true); c.onMediaLoaded(63_000)
        assertEquals(0, e.seekCalls)
        e.seekable = true; c.refresh()
        assertEquals(63_000L, e.positionMs)
        c.refresh()
        assertEquals(1, e.seekCalls)
    }
    @Test fun lateLoadingInBackgroundNeverStartsPlayback() {
        val c = VlcPlaybackController(); val e = Engine()
        c.bind(e, false); c.onMediaLoaded(0)
        assertEquals(0, e.playCalls)
        c.setForeground(true)
        assertTrue(e.playing)
    }
    @Test fun manuallyPausedPlaybackStaysPausedAcrossLifecycle() {
        val (c, e) = ready()
        c.setPlaying(false)
        c.setForeground(false); c.setForeground(true)
        assertFalse(e.playing)
        assertFalse(c.state.value.playRequested)
    }
    @Test fun bufferingIntentSurvivesBackgroundAndForeground() {
        val (c, e) = ready()
        e.playing = false // decoder is buffering, but the user did not pause
        c.setForeground(false)
        assertFalse(e.playing)
        c.setForeground(true)
        assertTrue(e.playing)
    }
    @Test fun trackChangesAreAppliedAndFailuresAreVisible() {
        val (c, e) = ready()
        c.selectAudio(2); c.selectSubtitle(4)
        assertEquals(2, e.audioTrackId)
        assertEquals(4, e.subtitleTrackId)
        c.selectSubtitle(-1)
        assertEquals(-1, e.subtitleTrackId)
        e.acceptTracks = false
        c.selectAudio(1)
        assertEquals(2, e.audioTrackId)
        assertNotNull(c.state.value.commandError)
        c.clearError(); assertNull(c.state.value.commandError)
    }
    @Test fun detachedOrReplacedEnginesNeverReceiveCommands() {
        val (c, old) = ready()
        val current = Engine()
        c.bind(current, true); c.onMediaLoaded(0)
        c.unbind(old)
        c.setPlaying(false)
        assertFalse(current.playing)
        assertTrue(old.playing)
        c.unbind(current)
        val calls = current.playCalls
        c.setPlaying(true); c.seekTo(5_000)
        assertEquals(calls, current.playCalls)
    }
    @Test fun timeLabelsHandleUnknownDurationAndLongMovies() {
        assertEquals("00:00", vodTimeLabel(-1))
        assertEquals("01:05", vodTimeLabel(65_000))
        assertEquals("2:01:05", vodTimeLabel(7_265_000))
        assertEquals(0L, boundedVodSeek(-10, 100))
        assertEquals(100L, boundedVodSeek(200, 100))
    }
}
