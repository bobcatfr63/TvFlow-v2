package com.tvflow.app.ui.vod

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompletionTest {
    @Test fun earlyEofDoesNotCompleteAnEpisode() {
        assertFalse(isCompletePlayback(24_000L, 3_030_000L))
        assertFalse(isCompletePlayback(0L, 3_030_000L))
    }
    @Test fun normalEndAllowsTheNextEpisodeDespiteFinalClockRounding() {
        assertTrue(isCompletePlayback(3_030_000L, 3_030_000L))
        assertTrue(isCompletePlayback(3_028_500L, 3_030_000L))
        assertFalse(isCompletePlayback(3_027_000L, 3_030_000L))
    }
    @Test fun unknownDurationKeepsEngineEndSemantics() {
        assertTrue(isCompletePlayback(24_000L, 0L))
    }
}
