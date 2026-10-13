package com.tvflow.app.data.repository

import com.tvflow.app.domain.LiveChannel
import java.text.Normalizer
import java.util.Locale

private val channelPrefix = Regex("^(?:(?:FR|FRANCE|EU|EUROPE|UK|US)\\s*[-|:]\\s*)+", RegexOption.IGNORE_CASE)
private val channelQualitySuffix = Regex("(?:\\s+|\\s*[-|]\\s*)(?:HD|FHD|UHD|4K|HEVC|SD|H265|H264)$", RegexOption.IGNORE_CASE)

/** Comparable display name, independent of IPTV provider prefixes and stream-quality labels. */
internal fun channelAlphabeticalKey(name: String): String {
    var value = name.trim()
    repeat(3) { value = value.replace(channelPrefix, "").trim() }
    value = value.replace(channelQualitySuffix, "").trim()
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
}

/** Numbered French TNT channels first, followed by other channels in stable provider order. */
internal fun sortTntChannels(channels: List<LiveChannel>): List<LiveChannel> =
    channels.filterNot { it.isSectionMarker }.sortedWith(
        compareBy<LiveChannel> {
            it.logicalNumber ?: frenchTntNumber(canonicalFrenchChannelName(it.name)) ?: Int.MAX_VALUE
        }
    )
