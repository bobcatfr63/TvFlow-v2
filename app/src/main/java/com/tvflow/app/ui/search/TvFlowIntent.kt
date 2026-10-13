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
        "suspense" to "thriller",
        "comedies" to "comedie",
        "comique" to "comedie",
        "humour" to "comedie",
        "francaise" to "francais",
        "francaises" to "francais",
        "policieres" to "police",
        "policiers" to "police",
        "sciencefiction" to "science fiction",
        "informatique" to "hacker",
        "piratage" to "hacker",
        "pirate" to "hacker",
        "cybersecurite" to "cyber",
        "programmation" to "code",
        "ordinateur" to "computer",
        "ordinateurs" to "computer"
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
            .flatMap { word -> (listOfNotNull(word, synonyms[word]) + topicTerms[word].orEmpty()).distinct() }.distinct().take(16)
        return Parsed(
            terms = terms,
            seriesOnly = tokens.any { it in setOf("serie", "series", "saison", "saisons", "episode") },
            filmOnly = tokens.any { it in setOf("film", "films") },
            recent = tokens.any { it.startsWith("recent") || it == "nouveau" }
        )
    }

    /** Two-word actor name after 'avec', independent of capitalization. */
    fun requestedPerson(input: String): String? {
        val words = normalized(input).split(' ')
        val after = words.indexOf("avec")
        if (after < 0 || after + 2 >= words.size) return null
        val first = words[after + 1]
        val second = words[after + 2]
        val excluded = setOf("des", "une", "les", "dans", "sans", "sous", "pour", "film", "serie", "plus", "beaucoup", "plein", "action", "comedie", "thriller", "suspense")
        if (first.length < 3 || second.length < 3 || first in excluded || second in excluded) return null
        return "$first $second"
    }

    /** Strict matching avoids a similar-looking title being mistaken for a credit. */
    fun sameMovie(creditedTitle: String, creditedYear: Int?, catalogTitle: String, catalogYear: Int?): Boolean {
        fun title(value: String): String = normalized(value)
            .replace(Regex("^(fr|en|vf|vostfr|multi|lat) "), "")
            .replace(Regex("\\b(19|20)\\d{2}\\b"), "")
            .replace(Regex("\\b(4k|uhd|fhd|hd|vf|vostfr)\\b"), "")
            .replace(Regex("\\s+"), " ").trim()
        if (title(creditedTitle) != title(catalogTitle)) return false
        return creditedYear == null || catalogYear == null || kotlin.math.abs(creditedYear - catalogYear) <= 1
    }

    private val topicTerms = mapOf(
        "informatique" to listOf("informatique", "piratage", "hacker", "hacking", "cyber", "ordinateur", "computer", "programmation"),
        "piratage" to listOf("piratage", "hacker", "hacking", "cyber"),
        "cybersecurite" to listOf("cybersecurite", "cyber", "hacker", "piratage"),
        "programmation" to listOf("programmation", "programmeur", "developpeur", "code"),
        "ordinateur" to listOf("ordinateur", "computer", "informatique")
    )

    fun searchTerms(input: String): List<String> {
        val words = normalized(input).split(' ').filter { it.length >= 3 && it !in stop && !it.startsWith("recent") && it != "nouveau" }
        val related = words.flatMap { topicTerms[it].orEmpty() }
        return (words + related + parse(input).terms).distinct().take(16)
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
