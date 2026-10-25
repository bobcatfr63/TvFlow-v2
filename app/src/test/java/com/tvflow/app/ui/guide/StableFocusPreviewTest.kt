package com.tvflow.app.ui.guide

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StableFocusPreviewTest {
    @Test fun previewStartsOnlyAfterTwoSeconds() = runTest {
        var preview: Int? = null
        val focus = StableFocusPreview(this) { preview = it }
        focus.focus(1)
        advanceTimeBy(1999); runCurrent(); assertNull(preview)
        advanceTimeBy(1); runCurrent(); assertEquals(1, preview)
        focus.cancel(); assertNull(preview)
    }
    @Test fun rapidFocusChangesCancelOlderRequests() = runTest {
        val played = mutableListOf<Int>()
        val focus = StableFocusPreview(this) { if (it != null) played += it }
        focus.focus(1); advanceTimeBy(1500)
        focus.focus(2); advanceTimeBy(1500)
        focus.focus(3); advanceTimeBy(2000); runCurrent()
        assertEquals(listOf(3), played)
        focus.cancel()
    }
    @Test fun leavingRowOrScreenCancelsPendingAndActivePreview() = runTest {
        var preview: Int? = null
        val focus = StableFocusPreview(this) { preview = it }
        focus.focus(1); advanceTimeBy(1000); focus.blur(1)
        advanceTimeBy(3000); runCurrent(); assertNull(preview)
        focus.focus(2); advanceTimeBy(2000); runCurrent(); assertEquals(2, preview)
        focus.blur(1); assertEquals(2, preview)
        focus.cancel(); assertNull(preview)
    }
    @Test fun movingBetweenEpgCellsOfSameRowDoesNotResetTimer() = runTest {
        var preview: Int? = null
        val focus = StableFocusPreview(this) { preview = it }
        focus.focus(1); advanceTimeBy(1500); focus.focus(1)
        advanceTimeBy(500); runCurrent(); assertEquals(1, preview)
        focus.cancel()
    }
}
