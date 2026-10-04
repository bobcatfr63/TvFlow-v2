package com.tvflow.app.data

import com.tvflow.app.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun newerVersionCode_isDetected() {
        assertTrue(
            isRemoteVersionNewer(
                "999.999.999",
                BuildConfig.VERSION_CODE.toLong() + 1L
            )
        )
    }

    @Test
    fun olderVersionCode_isIgnored_evenWithHigherSemanticName() {
        assertFalse(
            isRemoteVersionNewer(
                "999.999.999",
                (BuildConfig.VERSION_CODE.toLong() - 1L).coerceAtLeast(1L)
            )
        )
    }

    @Test
    fun sameVersionCode_isIgnored() {
        assertFalse(
            isRemoteVersionNewer(
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE.toLong()
            )
        )
    }

    @Test
    fun missingRemoteCode_isIgnoredForNonSemanticDevBuild() {
        assertFalse(isRemoteVersionNewer("999.0.0", 0L))
        assertFalse(isRemoteVersionNewer("0.0.1", 0L))
    }
}
