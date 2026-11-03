package com.tvflow.app.ui.common

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ExplicitLivePreviewTest {
    @Test fun focusAndFirstPressNeverStartPlayback() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val values = mutableListOf<Int?>()
        val gate = ExplicitLivePreview(scope, values::add)
        try {
            gate.focus(1)
            assertFalse(gate.press(1))
            delay(2_100)
            assertFalse(values.any { it != null })
        } finally { scope.cancel() }
    }

    @Test fun secondPressWaitsAndOnlyFollowingPressOpensFullscreen() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var published: Int? = null
        val gate = ExplicitLivePreview(scope) { published = it }
        try {
            assertFalse(gate.press(1))
            assertFalse(gate.press(1))
            delay(100)
            assertNull(published)
            delay(2_100)
            assertEquals(1, published)
            assertTrue(gate.press(1))
        } finally { scope.cancel() }
    }

    @Test fun movingFocusOrLeavingScreenCancelsPendingPlayback() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val values = mutableListOf<Int?>()
        val gate = ExplicitLivePreview(scope, values::add)
        try {
            gate.press(1); gate.press(1)
            gate.focus(2)
            gate.press(2); gate.press(2)
            gate.cancel()
            delay(2_100)
            assertFalse(values.any { it != null })
        } finally { scope.cancel() }
    }
}
