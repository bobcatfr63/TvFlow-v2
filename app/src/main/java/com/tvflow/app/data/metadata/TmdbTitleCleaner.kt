package com.tvflow.app.data.metadata

/** Strips IPTV packaging tags without deleting meaningful film subtitles. */
internal object TmdbTitleCleaner {
    private val prefix = Regex("""^(?:(?:\|?FR\|?|FRANCE|EN|VF|VOSTFR|MULTI)\s*[-:|]\s*)+""", RegexOption.IGNORE_CASE)
    private val squareTag = Regex("""\[([^]]*)]""")
    private val parentheses = Regex("""\(([^)]*)\)""")
    private val quality = Regex("""\b(?:FHD|UHD|4K|HD|SD|VF|VOSTFR|MULTI|CAM|TELESYNC|TS|WEBRIP|WEB-DL|BLURAY|BDRIP|HEVC|H\.?265|1080P|2160P)\b""", RegexOption.IGNORE_CASE)
    private val year = Regex("""\b(?:19|20)\d{2}\b""")
    private val noise = Regex("""^(?:FR|FRANCE|EN|VF|VOSTFR|MULTI|4K|UHD|FHD|HD|SD|HEVC|1080P|2160P|\d{4})$""", RegexOption.IGNORE_CASE)

    fun clean(input: String): String {
        var result = input.trim().replace(prefix, "")
        result = result.replace(squareTag) { match ->
            if (noise.matches(match.groupValues[1].trim())) " " else " " + match.groupValues[1] + " "
        }
        result = result.replace(parentheses) { match ->
            if (noise.matches(match.groupValues[1].trim())) " " else " " + match.groupValues[1] + " "
        }
        return result.replace(year, " ").replace(quality, " ")
            .replace(Regex("""[._|]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim(' ', '-', ':')
    }
}
