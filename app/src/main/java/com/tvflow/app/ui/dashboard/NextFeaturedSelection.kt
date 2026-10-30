package com.tvflow.app.ui.dashboard

import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem

/** One selection rule for the displayed title, actions and TMDB landscape lookup. */
internal fun selectNextFeaturedMedia(
    series: List<SeriesItem>,
    films: List<VodItem>
): Pair<SeriesItem?, VodItem?> {
    val selectedSeries = series.asSequence()
        .filter { isNextHeroCandidate(it.name) && !it.coverUrl.isNullOrBlank() }
        .minByOrNull { nextCatalogDisplayPriority(it.name) }
    val selectedFilm = if (selectedSeries == null) films.asSequence()
        .filter { isNextHeroCandidate(it.name) && !it.posterUrl.isNullOrBlank() }
        .minByOrNull { nextCatalogDisplayPriority(it.name) } else null
    return selectedSeries to selectedFilm
}
