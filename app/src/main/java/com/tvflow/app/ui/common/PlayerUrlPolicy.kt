package com.tvflow.app.ui.common

fun liveM3u8Fallback(url: String): String? {
    if (!url.endsWith(".ts", ignoreCase = true)) return null
    return url.dropLast(3) + ".m3u8"
}
