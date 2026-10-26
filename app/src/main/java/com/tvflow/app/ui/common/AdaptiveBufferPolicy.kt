package com.tvflow.app.ui.common

/** Shared bounded buffer policy for live, preview and on-demand Media3 playback. */
internal object AdaptiveBufferPolicy {
    data class Durations(val min: Int, val max: Int, val start: Int, val rebuffer: Int)

    fun durations(preset: String, isPreview: Boolean = false): Durations = when {
        isPreview -> Durations(1500, 6000, 400, 900)
        preset == "FAST" -> Durations(3000, 12000, 750, 1500)
        preset == "SAFE" -> Durations(8000, 30000, 2000, 3500)
        else -> Durations(5000, 20000, 1200, 2000) // AUTO starts at BALANCED
    }

    fun vlcCacheMs(preset: String): Int = when (preset) {
        "FAST" -> 750
        "SAFE" -> 3500
        else -> 2000 // AUTO starts at BALANCED too
    }

    /** Use sustained buffering evidence. Caller applies new level to the NEXT stream to avoid decoder reset. */
    fun nextLevel(level: Int, bufferingEvents: Int, stableMinutes: Int): Int = when {
        bufferingEvents >= 3 -> (level + 1).coerceAtMost(2)
        stableMinutes >= 15 && bufferingEvents == 0 -> (level - 1).coerceAtLeast(0)
        else -> level
    }
}
