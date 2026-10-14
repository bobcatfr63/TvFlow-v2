package com.tvflow.app.ui.search

import java.text.Normalizer
import java.util.Locale

/** Local recommendation heuristics, grounded exclusively in active catalog metadata. */
internal object TvFlowSimilarity {
    private val referencePattern = Regex(
        """\b(?:film|films|quelque chose|un truc)\s+comme\s+(.+)$|\b(?:similaire|semblable)\s+(?:a|au|aux)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val stopWords = setOf(
        "avec", "dans", "sans", "pour", "dont", "cette", "leur", "leurs", "mais",
        "sont", "etre", "avoir", "film", "histoire", "monde", "jeune", "homme",
        "femme", "entre", "apres", "avant", "elle", "ils", "elles", "plus",
        "tout", "tous", "comme", "parmi", "contre", "dont", "notre", "votre",
        "upon", "when", "with", "from", "that", "this", "their", "about", "into",
        "will", "have", "been", "they", "them", "movie", "story", "years"
    )
    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun referenceTitle(request: String): String? {
        val query = normalize(request)
        return referencePattern.find(query)?.groupValues?.drop(1)
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { it.length >= 3 }
    }

    fun keywords(synopsis: String?): List<String> =
        normalize(synopsis.orEmpty()).split(' ')
            .filter { it.length >= 5 && it !in stopWords && !it.matches(Regex("\\d+")) }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5).map { it.key }

    fun overlap(keywords: List<String>, title: String, synopsis: String?): Int {
        val tokens = (normalize(title) + " " + normalize(synopsis.orEmpty())).split(' ').toSet()
        return keywords.count { it in tokens }
    }
}
