package com.tvflow.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFlowIntentTest {
    @Test fun frenchRequestFindsGenreAndPeriod() {
        val intent = TvFlowIntent.parse("Je veux un film d'action récent avec beaucoup de suspense")
        assertTrue(intent.filmOnly)
        assertTrue(intent.recent)
        assertTrue(intent.terms.contains("action"))
        assertTrue(intent.terms.contains("thriller"))
    }
    @Test fun detectsSeriesAndAccents() {
        val intent = TvFlowIntent.parse("Une série policière avec plusieurs saisons")
        assertTrue(intent.seriesOnly)
        assertTrue(intent.terms.contains("police"))
    }
    @Test fun preservesActorAndTitle() {
        val intent = TvFlowIntent.parse("Un film avec Leonardo DiCaprio")
        assertEquals(listOf("leonardo", "dicaprio"), intent.terms)
    }
    @Test fun prefersCatalogMatches() {
        val intent = TvFlowIntent.parse("film invasion extraterrestre")
        assertTrue(TvFlowIntent.score(intent, "Alien Invasion", "alien", 2024) > 0)
        assertEquals(0, TvFlowIntent.score(intent, "Comédie romantique", null, null))
    }
    @Test fun preservesFrenchSynonymsForCatalogSearch() {
        val terms = TvFlowIntent.searchTerms("Un film avec une invasion extraterrestre")
        assertTrue(terms.contains("extraterrestre"))
        assertTrue(terms.contains("alien"))
        assertTrue(TvFlowIntent.score(TvFlowIntent.parse("extraterrestre"), "Invasion", "Invasion extraterrestre", null) > 0)
    }
    @Test fun recentIsNotAnUnrelatedMatch() {
        val intent = TvFlowIntent.parse("Un film d'action récent")
        assertEquals(0, TvFlowIntent.score(intent, "Romance", "Une histoire d'amour", 2026))
        assertTrue(TvFlowIntent.score(intent, "Action totale", null, 2024) >
            TvFlowIntent.score(intent, "Action totale", null, 2010))
        assertFalse(TvFlowIntent.searchTerms("film récent").contains("recent"))
    }
    @Test fun emptyRequestCannotReturnInventedRecommendations() {
        val intent = TvFlowIntent.parse("je veux un film")
        assertTrue(intent.terms.isEmpty())
        assertFalse(intent.seriesOnly)
    }
}
