package com.tvflow.app.ui.search

import java.text.Normalizer
import java.util.Locale

/** Local, deterministic intent analysis. Never manufactures catalog items. */
internal object TvFlowIntent {
    data class Parsed(val terms: List<String>, val seriesOnly: Boolean, val filmOnly: Boolean, val recent: Boolean, val requestedYear: Int? = null)

    private val stop = setOf(
        "je", "veux", "un", "une", "des", "de", "du", "la", "le", "les", "avec",
        "pour", "ce", "cette", "soir", "trouve", "moi", "regarder", "film",
        "films", "serie", "series", "qui", "dans", "beaucoup", "plusieurs",
        "comme", "ayant", "sur", "et", "en", "a", "au", "aux", "me", "propose",
        "bon", "bonne", "bons", "bonnes", "cherche", "recherche", "recommande",
        "interessant", "interessante", "interessants", "envie", "quelque", "chose",
        "plutot", "genre", "sorti", "sortie", "sortis", "sorties", "dernier", "derniere"
    )
    private val synonyms = mapOf(
        "enfant" to "famille",
        "enfants" to "famille",
        "familial" to "famille",
        "familiale" to "famille",
        "familiaux" to "famille",
        "jeunesse" to "famille",
        "sf" to "science",
        "extraterrestre" to "alien",
        "extraterrestres" to "alien",
        "policiere" to "police",
        "policier" to "police",
        "enquete" to "police",
        "enquetes" to "police",
        "dessin" to "animation",
        "anime" to "animation",
        "effrayant" to "horreur",
        "drole" to "comedie",
        "rigolo" to "comedie",
        "suspense" to "thriller",
        "mystere" to "mystere",
        "mysterieux" to "mystere",
        "mysterieuse" to "mystere",
        "romantique" to "romance",
        "romantiques" to "romance",
        "comedies" to "comedie",
        "comique" to "comedie",
        "humour" to "comedie",
        "francaise" to "francais",
        "francaises" to "francais",
        "policieres" to "police",
        "policiers" to "police",
        "sciencefiction" to "science",
        "informatique" to "hacker",
        "piratage" to "hacker",
        "pirate" to "hacker",
        "cybersecurite" to "cyber",
        "programmation" to "code",
        "actions" to "action",
        "aventures" to "aventure",
        "ordinateur" to "computer",
        "ordinateurs" to "computer",
        "polar" to "police",
        "detective" to "police",
        "angoissant" to "horreur",
        "marrant" to "comedie",
        "hilarant" to "comedie",
        "epouvante" to "horreur"
    )

    private fun normalized(value: String) =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun parse(input: String): Parsed {
        val normalized = normalized(input).replace(Regex("\\bscience fiction\\b"), "sciencefiction")
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        val terms = tokens.filterNot { it.length < 2 || it in stop || it.matches(Regex("(19|20)\\d{2}")) || it in setOf("recent", "recente", "recents", "recentes") }
            .flatMap { word -> (listOfNotNull(word, synonyms[word]) + topicTerms[word].orEmpty()).distinct() }.distinct().take(16)
        return Parsed(
            terms = terms,
            seriesOnly = tokens.any { it in setOf("serie", "series", "saison", "saisons", "episode") },
            filmOnly = tokens.any { it in setOf("film", "films") },
            recent = tokens.any { it.startsWith("recent") || it.startsWith("nouveau") || it.startsWith("nouvel") || it == "dernier" || it == "derniere" },
            requestedYear = tokens.firstOrNull { it.matches(Regex("(19|20)\\d{2}")) }?.toIntOrNull()
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
        val words = normalized(input).replace(Regex("\\bscience fiction\\b"), "sciencefiction").split(' ').filter { it.length >= 3 && it !in stop && !it.startsWith("recent") && !it.startsWith("nouveau") && !it.startsWith("nouvel") && it != "dernier" && it != "derniere" && !it.matches(Regex("(19|20)\\d{2}")) }
        val related = words.flatMap { topicTerms[it].orEmpty() }
        return (words + related + parse(input).terms).distinct().take(16)
    }

    private val genreIdsByTerm = mapOf(
        "action" to setOf(28, 10759),
        "aventure" to setOf(12, 10759),
        "animation" to setOf(16),
        "famille" to setOf(10751),
        "comedie" to setOf(35),
        "police" to setOf(80, 9648),
        "thriller" to setOf(53, 80, 9648),
        "mystere" to setOf(9648),
        "horreur" to setOf(27),
        "romance" to setOf(10749),
        "documentaire" to setOf(99),
        "fantastique" to setOf(14, 10765),
        "science" to setOf(878, 10765)
    )

    /** TMDB genre candidates, only for explicitly requested genres. */
    fun requestedGenreIds(query: Parsed): Set<Int> = query.terms.flatMap { genreIdsByTerm[it].orEmpty() }.toSet()

    /** Genre browsing can use provider category candidates without warming the full text index. */
    fun isGenreBrowsing(query: Parsed): Boolean =
        query.terms.any { it in genreIdsByTerm } &&
            query.terms.all { it in genreIdsByTerm || it in setOf("francais", "francaise") || synonyms[it] in genreIdsByTerm }

    fun score(
        query: Parsed, title: String, description: String?, year: Int?,
        categoryName: String? = null, tmdbGenreIds: String? = null
    ): Int {
        // An explicit release year is a hard constraint, never a loose ranking hint.
        if (query.requestedYear != null && year != query.requestedYear) return 0
        val name = normalized(title)
        val plot = normalized(description.orEmpty())
        val category = normalized(categoryName.orEmpty())
        val knownGenreIds = tmdbGenreIds.orEmpty().split(',').mapNotNull(String::toIntOrNull).toSet()
        fun genreMatches(term: String) = genreIdsByTerm[term]?.any { it in knownGenreIds } == true
        // Match whole words (and longer morphological forms), not arbitrary fragments:
        // "art" should never rank "carton" as a relevant movie.
        fun matches(text: String, term: String): Boolean {
            if (term.isBlank()) return false
            val normalizedTerm = normalized(term)
            return text.split(' ').any { word ->
                word == normalizedTerm || (normalizedTerm.length >= 5 && word.startsWith(normalizedTerm))
            }
        }
        // Requested genres are constraints, not optional ranking terms.
        // A French documentary must not pass a "comedie francaise" request
        // just because its title contains "francaise".
        val actionAdventure = query.terms.contains("action") && query.terms.contains("aventure")
        if (actionAdventure && (!matches(plot, "action") && !matches(category, "action") && !genreMatches("action") ||
                    !matches(plot, "aventure") && !matches(category, "aventure") && !genreMatches("aventure"))) return 0
        val requestedGenres = query.terms.filter { it in genreIdsByTerm }
        if (requestedGenres.any { term ->
                // Explicit TMDB genres are stronger evidence than a passing word in a synopsis.
                // For incomplete metadata, retain the existing category/title/plot fallback.
                if (knownGenreIds.isNotEmpty()) !genreMatches(term)
                else !matches(category, term) && !matches(plot, term) && !matches(name, term)
            }) return 0
        val matching = query.terms.sumOf { term ->
            when {
                genreMatches(term) -> 25
                matches(category, term) -> 16
                matches(name, term) -> 12
                matches(plot, term) -> 5
                else -> 0
            }
        }
        // Recency is a ranking bonus, never proof that a title matches the request.
        if (matching == 0) return 0
        return matching + if (query.recent && year != null && year >= 2020) 4 else 0
    }
}
