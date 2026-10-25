package com.tvflow.app.ui.player

import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.StreamHealth
import com.tvflow.app.data.repository.selectTntChannels
import org.junit.Assert.*
import org.junit.Test

class LivePlaybackQueueTest {
    private fun channel(id: Int, name: String) = LiveChannel(id, name, "provider", null, null)

    @Test fun tntHealthUpdateCannotRemovePlayingVariantFromQueue() {
        val hd = channel(1, "TF1 HD").copy(health = StreamHealth.ONLINE)
        val sd = channel(2, "TF1 SD")
        val france2 = channel(3, "France 2")
        val displayed = listOf(hd, france2) // Snapshot of the already-displayed two-channel TV queue
        val queue = LivePlaybackQueue()
        queue.capture("__tnt__", displayed)
        val playing = queue.consume("__tnt__", hd.streamId)!!
        val changed = selectTntChannels(listOf(hd.copy(health = StreamHealth.OFFLINE), sd, france2))
        assertTrue(playing.any { it.streamId == hd.streamId }) // Immutable handoff despite external updates
        assertEquals(playing[1].streamId, adjacentChannelId(playing, 1, 1))
        assertEquals(playing.last().streamId, adjacentChannelId(playing, 1, -1))
        assertEquals(1, adjacentChannelId(playing, playing.last().streamId, 1))
    }

    @Test fun filteredCustomOrderAndFavoritesAreHandedOffExactlyOnce() {
        val queue = LivePlaybackQueue()
        val rows = mutableListOf(channel(9, "Z"), channel(4, "A"), channel(8, "B"))
        queue.capture("custom-filter", rows)
        rows.clear()
        val snapshot = queue.consume("custom-filter", 4)!!
        assertEquals(listOf(9, 4, 8), snapshot.map { it.streamId })
        assertEquals(8, adjacentChannelId(snapshot, 4, 1))
        assertEquals(9, adjacentChannelId(snapshot, 4, -1))
        assertNull(queue.consume("custom-filter", 4))
    }

    @Test fun unrelatedLaunchCannotReuseOldContext() {
        val queue = LivePlaybackQueue()
        queue.capture("__favorites__", listOf(channel(1, "TF1")))
        assertNull(queue.consume("other", 1))
        assertNull(queue.consume("__favorites__", 1))
        assertNull(adjacentChannelId(emptyList(), 1, 1))
        assertNull(adjacentChannelId(listOf(channel(1, "TF1")), 99, 1))
    }
}
