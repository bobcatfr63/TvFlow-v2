package com.tvflow.app.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFallbackTest {
    @Test
    fun fallback_isEnabledForDecoderOrSourceFailure() {
        assertTrue(shouldUseVlcFallback(null, "ERROR_CODE_DECODING_FAILED"))
        assertTrue(shouldUseVlcFallback(null, "ERROR_CODE_IO_UNSPECIFIED"))
    }

    @Test
    fun fallback_doesNotHideHttpClientErrors() {
        assertFalse(shouldUseVlcFallback(401, "ERROR_CODE_IO_BAD_HTTP_STATUS"))
        assertFalse(shouldUseVlcFallback(403, "ERROR_CODE_IO_BAD_HTTP_STATUS"))
        assertFalse(shouldUseVlcFallback(429, "ERROR_CODE_IO_BAD_HTTP_STATUS"))
    }

    @Test
    fun fallback_requiresAnActualPlaybackError() {
        assertFalse(shouldUseVlcFallback(null, null))
        assertFalse(shouldUseVlcFallback(null, ""))
    }
}
