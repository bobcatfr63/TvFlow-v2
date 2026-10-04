package com.tvflow.app.data.repository

import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.StreamHealth
import java.text.Normalizer

private val FORBIDDEN_QUALITY = Regex(
    "(?i)(^|[^A-Z0-9])(4K|UHD|FHD|FULL\\s*HD|2160P?)(?=$|[^A-Z0-9])"
)
private val FRENCH_PREFIX = Regex("(?i)^\\s*(?:\\|?FR\\|?|FRANCE)\\s*[-:|]\\s*")
private val QUALITY_TOKEN = Regex(
    "(?i)(^|[^A-Z0-9])(HD|SD|HEVC|H\\.?265|H\\.?264|FHD|FULL\\s*HD|UHD|4K|1080P?|2160P?)(?=$|[^A-Z0-9])"
)
private val NORMALIZED_FRENCH_PREFIX = Regex("^\\|?FR\\|?\\s*[-:|]?\\s*")
private val NORMALIZED_FRANCE_PREFIX = Regex("^FRANCE\\s*[-:|]\\s*")
private val BRACKETED_TOKEN = Regex("\\[[^]]*]")
private val MULTI_SPACE = Regex("\\s+")
private val HD_TOKEN = Regex("(^|[^A-Z0-9])HD(?=$|[^A-Z0-9])")
private val SD_TOKEN = Regex("(^|[^A-Z0-9])SD(?=$|[^A-Z0-9])")
private val DIACRITICS = Regex("\\p{M}+")
private val NON_CHANNEL_CHARS = Regex("[^A-Z0-9+'|:.-]+")

private data class FrenchCandidate(
    val channel: LiveChannel,
    val canonicalName: String,
    val score: Int
)

private data class PresentationCacheKey(
    val size: Int,
    val fingerprint: Long,
    val firstStreamId: Int?,
    val lastStreamId: Int?
)

private const val PRESENTATION_CACHE_SIZE = 8
private val presentationCache = object : LinkedHashMap<PresentationCacheKey, List<LiveChannel>>(
    PRESENTATION_CACHE_SIZE,
    0.75f,
    true
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PresentationCacheKey, List<LiveChannel>>?): Boolean =
        size > PRESENTATION_CACHE_SIZE
}

internal fun prepareFrenchChannelsCached(channels: List<LiveChannel>): List<LiveChannel> {
    if (channels.isEmpty()) return emptyList()
    val key = PresentationCacheKey(
        size = channels.size,
        fingerprint = presentationFingerprint(channels),
        firstStreamId = channels.firstOrNull()?.streamId,
        lastStreamId = channels.lastOrNull()?.streamId
    )
    synchronized(presentationCache) {
        presentationCache[key]?.let { return it }
    }
    val prepared = prepareFrenchChannels(channels)
    synchronized(presentationCache) {
        presentationCache[key] = prepared
    }
    return prepared
}

private fun presentationFingerprint(channels: List<LiveChannel>): Long {
    var hash = 1_125_899_906_842_597L
    channels.forEach { channel -> hash = 31L * hash + channel.hashCode() }
    return hash
}

internal fun prepareFrenchChannels(channels: List<LiveChannel>): List<LiveChannel> {
    val bestByCanonical = LinkedHashMap<String, FrenchCandidate>()

    channels.forEach { channel ->
        if (FORBIDDEN_QUALITY.containsMatchIn(channel.name)) return@forEach
        val normalized = normalize(channel.name)
        val canonical = canonicalFrenchChannelName(normalized, alreadyNormalized = true)
        if (!isFrenchChannel(channel.name, canonical)) return@forEach

        val candidate = FrenchCandidate(
            channel = channel,
            canonicalName = canonical,
            score = variantScore(channel, normalized)
        )
        val current = bestByCanonical[canonical]
        if (current == null || candidate.score > current.score) {
            bestByCanonical[canonical] = candidate
        }
    }

    return bestByCanonical.values
        .sortedWith(
            compareBy<FrenchCandidate>(
                { frenchTntNumber(it.canonicalName) ?: Int.MAX_VALUE },
                { it.canonicalName },
                { it.channel.name.lowercase() }
            )
        )
        .map { candidate ->
            candidate.channel.copy(logicalNumber = frenchTntNumber(candidate.canonicalName))
        }
}

internal fun isFrenchCategoryName(name: String): Boolean {
    val normalized = normalize(name)
    return normalized == "FRANCE" || normalized.startsWith("FRANCE ")
}

internal fun canonicalFrenchChannelName(name: String): String =
    canonicalFrenchChannelName(name, alreadyNormalized = false)

private fun canonicalFrenchChannelName(name: String, alreadyNormalized: Boolean): String {
    var value = if (alreadyNormalized) name else normalize(name)
    value = value.replace(NORMALIZED_FRENCH_PREFIX, "")
    value = value.replace(NORMALIZED_FRANCE_PREFIX, "")
    value = value.replace(BRACKETED_TOKEN, " ")
    value = value.replace(QUALITY_TOKEN, " ")
    value = value.replace(MULTI_SPACE, " ").trim()

    return when (value) {
        "BFMTV" -> "BFM TV"
        "FRANCE INFO", "FRANCEINFO", "FRANCEINFO:" -> "FRANCEINFO"
        "L EQUIPE", "LEQUIPE", "L'EQUIPE" -> "L EQUIPE"
        "LCP", "LCP PUBLIC SENAT", "LCP-PUBLIC SENAT" -> "LCP PUBLIC SENAT"
        "TF1 SERIES FILMS", "TF1 SERIE FILMS" -> "TF1 SERIES FILMS"
        "RMC DECOUVERTE" -> "RMC DECOUVERTE"
        "CHERIE25" -> "CHERIE 25"
        else -> value
    }
}

internal fun frenchTntNumber(canonicalName: String): Int? = when (canonicalName) {
    "TF1" -> 1
    "FRANCE 2" -> 2
    "FRANCE 3" -> 3
    "FRANCE 4" -> 4
    "FRANCE 5" -> 5
    "M6" -> 6
    "ARTE" -> 7
    "LCP PUBLIC SENAT" -> 8
    "W9" -> 9
    "TMC" -> 10
    "TFX" -> 11
    "GULLI" -> 12
    "BFM TV" -> 13
    "CNEWS" -> 14
    "LCI" -> 15
    "FRANCEINFO" -> 16
    "CSTAR" -> 17
    "T18" -> 18
    "NOVO19" -> 19
    "TF1 SERIES FILMS" -> 20
    "L EQUIPE" -> 21
    "6TER" -> 22
    "RMC STORY" -> 23
    "RMC DECOUVERTE" -> 24
    "CHERIE 25" -> 25
    else -> null
}

private fun isFrenchChannel(rawName: String, canonicalName: String): Boolean {
    if (FRENCH_PREFIX.containsMatchIn(rawName.trim())) return true
    return frenchTntNumber(canonicalName) != null
}

private fun variantScore(channel: LiveChannel, normalized: String): Int {
    var score = when (channel.health) {
        StreamHealth.ONLINE -> 100
        StreamHealth.UNKNOWN -> 80
        StreamHealth.CHECKING -> 75
        StreamHealth.UNSTABLE -> 60
        StreamHealth.UNPLAYABLE -> 20
        StreamHealth.OFFLINE -> 0
    }
    if (!channel.epgChannelId.isNullOrBlank()) score += 20

    score += when {
        HD_TOKEN.containsMatchIn(normalized) -> 18
        SD_TOKEN.containsMatchIn(normalized) -> 6
        else -> 12
    }
    if ("HEVC" in normalized || "H265" in normalized || "H.265" in normalized) score -= 2
    return score
}

private fun normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .uppercase()
        .replace('’', '\'')
        .replace(NON_CHANNEL_CHARS, " ")
        .replace(MULTI_SPACE, " ")
        .trim()
