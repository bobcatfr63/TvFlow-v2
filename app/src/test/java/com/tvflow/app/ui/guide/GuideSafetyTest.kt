package com.tvflow.app.ui.guide

import com.tvflow.app.domain.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideSafetyTest {
    @Test
    fun visibleEpgIds_handlesEmptyListWithStaleLayoutIndices() {
        assertTrue(
            safeVisibleEpgIds(
                channels = emptyList(),
                firstVisibleIndex = 12,
                lastVisibleIndex = 18
            ).isEmpty()
        )
    }

    @Test
    fun visibleEpgIds_clampsStaleIndicesAfterListShrinks() {
        val channels = (1..4).map { channel(it) }

        assertEquals(
            listOf(4),
            safeVisibleEpgIds(
                channels = channels,
                firstVisibleIndex = 40,
                lastVisibleIndex = 45
            )
        )
    }

    @Test
    fun timelineObservation_followsVisibleAndSelectedChannels() {
        val channels = (1..50).map { channel(it) }

        val result = resolveTimelineStreamIds(
            channels = channels,
            visibleIds = listOf(35, 36, 37, 38),
            selectedId = 42
        )

        assertEquals(listOf(35, 36, 37, 38, 42), result)
    }

    @Test
    fun timelineObservation_defaultsToInitialWindowBeforeLayoutIsReady() {
        val channels = (1..20).map { channel(it) }

        val result = resolveTimelineStreamIds(
            channels = channels,
            visibleIds = emptyList(),
            selectedId = null
        )

        assertEquals((1..12).toList(), result)
    }

    private fun channel(id: Int) = LiveChannel(
        streamId = id,
        name = "Channel $id",
        categoryId = "1",
        logoUrl = null,
        epgChannelId = "epg-$id"
    )
}
