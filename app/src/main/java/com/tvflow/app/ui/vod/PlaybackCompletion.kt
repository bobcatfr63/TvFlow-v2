package com.tvflow.app.ui.vod

/** An early network EOF must preserve resume progress instead of completing the title. */
internal fun isCompletePlayback(positionMs: Long, durationMs: Long): Boolean =
    durationMs <= 0L || positionMs >= (durationMs - 2_000L).coerceAtLeast(0L)
