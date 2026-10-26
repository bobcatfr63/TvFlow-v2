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

/** Quality inferred from the provider's label; actual resolution is available only during playback. */
internal fun tntQualityRank(name: String): Int {
    val upper = name.uppercase(Locale.ROOT)
    fun has(label: String) = Regex("(?<![A-Z0-9])(?:" + label + ")(?![A-Z0-9])").containsMatchIn(upper)
    return when {
        has("4K|UHD|2160P") -> 4
        has("FHD|FULL\\s*HD|1080[PI]") -> 3
        has("HD|720[PI]") -> 2
        has("SD|576[PI]") -> 1
        else -> 0
    }
}

/** One French TNT stream per recognised channel, regardless of IPTV category.
 * Never trust arbitrary provider logical numbers as proof of TNT membership.
 */
internal fun selectTntChannels(channels: List<LiveChannel>): List<LiveChannel> {
    return channels.asSequence()
        .filterNot { it.isSectionMarker || it.isHidden }
        .mapNotNull { channel ->
            val number = frenchTntNumber(canonicalFrenchChannelName(channel.name)) ?: return@mapNotNull null
            number to channel
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .values
        .map { variants ->
            variants.maxWithOrNull(compareBy<LiveChannel> {
                when (it.health) {
                    com.tvflow.app.domain.StreamHealth.ONLINE -> 4
                    com.tvflow.app.domain.StreamHealth.UNKNOWN -> 3
                    com.tvflow.app.domain.StreamHealth.CHECKING -> 2
                    com.tvflow.app.domain.StreamHealth.UNSTABLE -> 1
                    else -> 0
                }
            }.thenBy { tntQualityRank(it.name) }
                .thenBy { if (it.epgChannelId.isNullOrBlank()) 0 else 1 })!!
        }
}

/** Numbered French TNT channels first, followed by other channels in stable provider order. */
internal fun sortTntChannels(channels: List<LiveChannel>): List<LiveChannel> =
    channels.filterNot { it.isSectionMarker }.sortedWith(
        compareBy<LiveChannel> {
            it.logicalNumber ?: frenchTntNumber(canonicalFrenchChannelName(it.name)) ?: Int.MAX_VALUE
        }
    )
