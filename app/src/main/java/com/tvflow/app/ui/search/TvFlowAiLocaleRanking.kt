package com.tvflow.app.ui.search

/** A slight locale preference; never fabricates matches and never overrides hidden categories. */
internal object TvFlowAiLocaleRanking {
    private val international = Regex("""(?i)\b(?:etranger|etrangers|etrangere|etrangeres|international|internationaux|monde|mondial|mondiale|anglais|anglais[e]s?|americain|americains|americaine|americaines|worldwide|global)\b""")
    private val frenchCategory = Regex("""(?i)(?:\[FR\]|\bFRANCE\b|^FR\s*[-:|])""")

    fun requestsInternational(query: String): Boolean {
        val normalized = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
        return international.containsMatchIn(normalized)
    }

    fun frenchBonus(category: String?, preferFrench: Boolean): Int =
        if (preferFrench && category != null && frenchCategory.containsMatchIn(category)) 6 else 0
}
