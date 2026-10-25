package com.tvflow.app.ui.player

import com.tvflow.app.domain.LiveChannel
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot handoff of the displayed order. Never contains stream URLs or credentials. */
@Singleton
class LivePlaybackQueue @Inject constructor() {
    private var pending: Pair<String, List<LiveChannel>>? = null

    fun capture(source: String, channels: List<LiveChannel>) {
        pending = source to channels.filterNot { it.isSectionMarker || it.isHidden }
            .distinctBy { it.streamId }
    }

    fun consume(source: String, streamId: Int): List<LiveChannel>? {
        val snapshot = pending
        pending = null
        return snapshot?.takeIf { it.first == source && it.second.any { c -> c.streamId == streamId } }?.second
    }
}

internal fun adjacentChannelId(channels: List<LiveChannel>, selectedId: Int, delta: Int): Int? {
    val index = channels.indexOfFirst { it.streamId == selectedId }
    if (index < 0 || channels.size < 2) return null
    return channels[Math.floorMod(index + delta, channels.size)].streamId
}
