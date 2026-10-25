package com.tvflow.app.ui.search

/** AI may add lookups; filtering and ordering always use the original intent. */
internal object TvFlowAiQueryPolicy {
    /** Save the free quota: simple title, genre and short searches stay entirely local. */
    fun needsGroq(raw: String): Boolean {
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (raw.length !in 24..240 || words.size < 6) return false
        val complex = Regex("(?i)\\b(qui|dont|sans|mais|plutot|ambiance|similaire|ressemble|histoire|parle|sujet|personnage|fin|surprise|melange|genre)\\b")
        return complex.containsMatchIn(java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")) || words.size >= 10
    }
    fun searchTerms(raw: String, rewritten: String?): List<String> {
        val original = TvFlowIntent.searchTerms(raw).take(6)
        val hints = rewritten?.let(TvFlowIntent::searchTerms).orEmpty()
        return (original + hints + TvFlowIntent.searchTerms(raw)).distinct().take(8)
    }
}
