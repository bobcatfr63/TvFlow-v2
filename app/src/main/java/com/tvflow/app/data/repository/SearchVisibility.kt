package com.tvflow.app.data.repository

/** Small preference snapshot; never copy full catalog entities to apply visibility. */
internal data class SearchVisibility(
    val hiddenMediaKeys: Set<String> = emptySet(),
    val hiddenLiveCategoryIds: Set<String> = emptySet(),
    val hiddenLiveStreamIds: Set<Int> = emptySet()
) {
    fun hiddenCategories(type: String): Set<String> {
        val prefix = "$type:"
        return hiddenMediaKeys.asSequence().filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }.toSet()
    }
}

internal data class SearchIndexEntry(val key: String, val id: Int, val categoryId: String)

/** Visibility must precede top-K: hidden matches must not consume the result quota. */
internal fun findVisibleSearchIds(
    source: List<SearchIndexEntry>, query: String, limitPerType: Int,
    hiddenCategoryIds: Set<String>, hiddenStreamIds: Set<Int> = emptySet()
): List<Int> {
    val limit = limitPerType.coerceIn(0, 150)
    if (limit == 0) return emptyList()
    data class Hit(val offset: Int, val position: Int, val id: Int)
    val worstFirst = compareByDescending<Hit> { it.offset }.thenByDescending { it.position }
    val bestFirst = compareBy<Hit> { it.offset }.thenBy { it.position }
    val best = java.util.PriorityQueue(worstFirst)
    source.forEachIndexed { position, entry ->
        if (entry.categoryId in hiddenCategoryIds || entry.id in hiddenStreamIds) return@forEachIndexed
        val offset = entry.key.indexOf(query)
        if (offset < 0) return@forEachIndexed
        val hit = Hit(offset, position, entry.id)
        if (best.size < limit) best.add(hit)
        else if (bestFirst.compare(hit, best.peek()) < 0) {
            best.poll()
            best.add(hit)
        }
    }
    return best.sortedWith(bestFirst).map { it.id }
}
