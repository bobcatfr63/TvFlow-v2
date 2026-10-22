package com.tvflow.app.data.metadata

/** Strips IPTV packaging tags without deleting meaningful film subtitles. */
internal object TmdbTitleCleaner {
    private val prefix = Regex("""^(?:(?:\|?FR\|?|FRANCE|EN|VF|VOSTFR|MULTI)\s*[-:|]\s*)+""", RegexOption.IGNORE_CASE)
    private val leadingTag = Regex("""^(?:\s*(?:\[(?:FR|FRANCE|EN|VF|VOSTFR|MULTI|4K|UHD|FHD|HD)\]|\((?:FR|FRANCE|EN|VF|VOSTFR|MULTI)\)|\|(?:FR|FRANCE|EN|VF|VOSTFR|MULTI)\|)\s*)+""", RegexOption.IGNORE_CASE)
    private val trailingCountry = Regex("""(?i)\s*\((?:FR|US|UK|CA|DE|MX|ES|IT|BE|CH|JP|KR|TR|IN|AU)\)\s*$""")
    private val squareTag = Regex("""\[([^]]*)]""")
    private val parentheses = Regex("""\(([^)]*)\)""")
    private val quality = Regex("""\b(?:FHD|UHD|4K|HD|SD|VF|VOSTFR|MULTI|CAM|TELESYNC|TS|WEBRIP|WEB-DL|BLURAY|BDRIP|HEVC|H\.?265|1080P|2160P)\b""", RegexOption.IGNORE_CASE)
    private val year = Regex("""\b(?:19|20)\d{2}\b""")
    private val releaseNoise = Regex("""(?i)\b(?:DDP?\s*5[.]1|AAC\s*2[.]0|X264|X265|H264|H265|10BIT|HDR(?:10)?|DOLBY\s*VISION|ATMOS|PROPER|REPACK)\b""")
    private val seasonEpisode = Regex("""(?i)\bS\d{1,2}E\d{1,3}\b|\bS\d{1,2}\b""")
    private val noise = Regex("""^(?:FR|FRANCE|EN|VF|VOSTFR|MULTI|4K|UHD|FHD|HD|SD|HEVC|1080P|2160P|\d{4})$""", RegexOption.IGNORE_CASE)

    fun clean(input: String): String {
        var result = input.trim().replace(trailingCountry, "").replace(leadingTag, "").replace(prefix, "")
        result = result.replace(squareTag) { match ->
            if (noise.matches(match.groupValues[1].trim())) " " else " " + match.groupValues[1] + " "
        }
        result = result.replace(parentheses) { match ->
            if (noise.matches(match.groupValues[1].trim())) " " else " " + match.groupValues[1] + " "
        }
        return result.replace(year, " ").replace(quality, " ")
            .replace(releaseNoise, " ").replace(seasonEpisode, " ")
            .replace(Regex("""[._|]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim(' ', '-', ':')
    }
}
