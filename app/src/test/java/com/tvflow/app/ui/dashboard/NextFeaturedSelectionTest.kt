package com.tvflow.app.ui.dashboard

import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import org.junit.Assert.*
import org.junit.Test

class NextFeaturedSelectionTest {
    private fun series(id: Int, title: String, cover: String? = "https://example.com/poster") =
        SeriesItem(id, title, "series", cover, null, null)

    @Test fun frenchRecentTitleWinsOverEarlierEnglishRecommendation() {
        val english = series(1, "EN - Breaking Bad")
        val french = series(2, "FR - Agence Acapulco")
        val selected = selectNextFeaturedMedia(listOf(english, french), emptyList())
        assertEquals(french, selected.first)
        assertNull(selected.second)
    }

    @Test fun missingSeriesArtworkAllowsMovieFallback() {
        val film = VodItem(3, "FR - Inception", "films", "https://example.com/poster", null, null, null)
        val selected = selectNextFeaturedMedia(listOf(series(1, "Breaking Bad", null)), listOf(film))
        assertNull(selected.first)
        assertEquals(film, selected.second)
    }

    @Test fun emptyCatalogHasNoFeaturedLookup() {
        assertEquals(null to null, selectNextFeaturedMedia(emptyList(), emptyList()))
    }
}
