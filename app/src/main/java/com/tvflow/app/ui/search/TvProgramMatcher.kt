package com.tvflow.app.ui.search

import com.tvflow.app.domain.EpgProgram
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/** EPG-only suggestions. A program is never reported unless it exists in local EPG. */
internal object TvProgramMatcher {
    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun isTvQuestion(text: String): Boolean {
        val q = normalize(text)
        return listOf("ce soir", "a la tele", "a la tv", "sur quelle chaine", "sur les chaines",
            "en direct", "maintenant", "passe a", "diffuse", "programme tv", "au programme").any(q::contains)
    }

    fun window(text: String, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        val q = normalize(text)
        if (q.contains("ce soir") || q.contains("soir")) {
            calendar.set(Calendar.HOUR_OF_DAY, 18)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = maxOf(nowMs, calendar.timeInMillis) / 1000
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 2)
            return start to (calendar.timeInMillis / 1000)
        }
        return nowMs / 1000 to (nowMs / 1000 + 3 * 3600)
    }

    fun score(request: String, program: EpgProgram): Int {
        val q = normalize(request)
        val haystack = normalize(program.title + " " + program.description.orEmpty())
        val sports = q.contains("foot") || q.contains("match")
        val genre = when {
            sports -> listOf("football", "foot", "ligue", "match")
            q.contains("action") -> listOf("action", "aventure", "combat", "thriller")
            q.contains("comedie") -> listOf("comedie", "humour", "humoristique")
            q.contains("documentaire") -> listOf("documentaire", "reportage")
            q.contains("policier") -> listOf("policier", "enquete", "crime")
            q.contains("thriller") -> listOf("thriller", "suspense")
            else -> emptyList()
        }
        val terms = q.split(' ').filter { it.length > 3 && it !in setOf("soir", "film", "chaine", "passe", "direct", "avec", "programme", "maintenant", "quelle", "estce", "quil", "diffuse") }
        return genre.sumOf { if (haystack.contains(it)) 9 else 0 } +
            terms.sumOf { if (haystack.contains(it)) 3 else 0 }
    }
}
