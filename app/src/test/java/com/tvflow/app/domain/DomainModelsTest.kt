package com.tvflow.app.domain

import com.tvflow.app.ui.common.liveM3u8Fallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {
    @Test
    fun epgProgram_isLiveAt_usesHalfOpenInterval() {
        val program = EpgProgram(
            streamId = 42,
            startTimestamp = 1_000L,
            stopTimestamp = 2_000L,
            title = "Test",
            description = null
        )

        assertFalse(program.isLiveAt(999L))
        assertTrue(program.isLiveAt(1_000L))
        assertTrue(program.isLiveAt(1_999L))
        assertFalse(program.isLiveAt(2_000L))
    }

    @Test
    fun liveChannel_sectionMarker_isDetectedAndNamed() {
        val section = LiveChannel(
            streamId = 7,
            name = "##### CINEMA HEVC #####",
            categoryId = "1",
            logoUrl = null,
            epgChannelId = null
        )
        val channel = section.copy(name = "France 2")

        assertTrue(section.isSectionMarker)
        assertEquals("CINEMA HEVC", section.sectionTitle)
        assertFalse(channel.isSectionMarker)
        assertEquals(null, channel.sectionTitle)
    }

    @Test
    fun parseXtreamM3uUrl_extractsCredentialsAndOutput() {
        val parsed = parseXtreamM3uUrl(
            "http://example.test/get.php?username=test-user&password=test-pass&type=m3u_plus&output=ts"
        )

        assertEquals("http://example.test", parsed?.serverUrl)
        assertEquals("test-user", parsed?.username)
        assertEquals("test-pass", parsed?.password)
        assertEquals("ts", parsed?.outputFormat)
    }

    @Test
    fun liveM3u8Fallback_replacesTsExtensionOnly() {
        assertEquals(
            "http://server/live/user/pass/123.m3u8",
            liveM3u8Fallback("http://server/live/user/pass/123.ts")
        )
        assertEquals(null, liveM3u8Fallback("http://server/live/user/pass/123.m3u8"))
    }

    @Test
    fun playbackPosition_progress_isClamped() {
        val normal = playback(positionMs = 50L, durationMs = 100L)
        assertEquals(0.5f, normal.progress, 0.0001f)

        val pastEnd = playback(positionMs = 150L, durationMs = 100L)
        assertEquals(1f, pastEnd.progress, 0.0001f)

        val invalidDuration = playback(positionMs = 50L, durationMs = 0L)
        assertEquals(0f, invalidDuration.progress, 0.0001f)
    }

    private fun playback(positionMs: Long, durationMs: Long) = PlaybackPosition(
        mediaKey = "vod:1",
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = 0L,
        title = "Film",
        imageUrl = null,
        kind = "vod",
        mediaId = "1",
        containerExtension = "mp4"
    )
}
