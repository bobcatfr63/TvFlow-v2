package com.tvflow.app.ui.search

import java.text.Normalizer
import java.util.Locale

/** Local, deterministic intent analysis. Never manufactures catalog items. */
internal object TvFlowIntent {
    data class Parsed(val terms: List<String>, val seriesOnly: Boolean, val filmOnly: Boolean, val recent: Boolean)

    private val stop = setOf(
        "je", "veux", "un", "une", "des", "de", "du", "la", "le", "les", "avec",
        "pour", "ce", "cette", "soir", "trouve", "moi", "regarder", "film",
        "films", "serie", "series", "qui", "dans", "beaucoup", "plusieurs",
        "comme", "ayant", "sur", "et", "en", "a", "au", "aux", "me", "propose"
    )
    private val synonyms = mapOf(
        "extraterrestre" to "alien",
        "extraterrestres" to "alien",
        "policiere" to "police",
        "policier" to "police",
        "dessin" to "animation",
        "anime" to "animation",
        "effrayant" to "horreur",
        "drole" to "comedie",
        "rigolo" to "comedie",
        "suspense" to "thriller"
    )

    private fun normalized(value: String) =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun parse(input: String): Parsed {
        val normalized = normalized(input)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        val terms = tokens.filterNot { it.length < 2 || it in stop || it in setOf("recent", "recente", "recents", "recentes") }
            .flatMap { word -> listOfNotNull(word, synonyms[word]).distinct() }.distinct().take(12)
        return Parsed(
            terms = terms,
            seriesOnly = tokens.any { it in setOf("serie", "series", "saison", "saisons", "episode") },
            filmOnly = tokens.any { it in setOf("film", "films") },
            recent = tokens.any { it.startsWith("recent") || it == "nouveau" }
        )
    }

    /** Detects an explicit two-part proper name after 'avec', without treating genres as actors. */
    fun requestedPerson(input: String): String? = Regex("(?i)\\bavec\\s+([A-ZÀ-Ÿ][a-zà-ÿ]+)\\s+([A-ZÀ-Ÿ][a-zà-ÿ]+)")
        .find(input)?.let { match ->
            val first = match.groupValues[1]
            val second = match.groupValues[2]
            if (first.first().isUpperCase() && second.first().isUpperCase()) "$first $second" else null
        }

    fun searchTerms(input: String): List<String> {
        val words = normalized(input).split(' ').filter { it.length >= 3 && it !in stop && !it.startsWith("recent") && it != "nouveau" }
        return (words + parse(input).terms).distinct().take(12)
    }

    fun score(query: Parsed, title: String, description: String?, year: Int?): Int {
        val name = normalized(title)
        val plot = normalized(description.orEmpty())
        val matching = query.terms.sumOf { term ->
            when {
                name.contains(term) -> 12
                plot.contains(term) -> 5
                else -> 0
            }
        }
        // Recency is a ranking bonus, never proof that a title matches the request.
        if (matching == 0) return 0
        return matching + if (query.recent && year != null && year >= 2020) 4 else 0
    }
}
