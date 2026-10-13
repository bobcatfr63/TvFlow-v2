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
    @Test fun extractsActorWithoutConfusingGenre() {
        assertEquals("johnny depp", TvFlowIntent.requestedPerson("trouve-moi un film d'action avec Johnny Depp"))
        assertEquals("tom cruise", TvFlowIntent.requestedPerson("film avec tom cruise"))
        assertEquals("tom cruise", TvFlowIntent.requestedPerson("film avec TOM CRUISE"))
        assertEquals(null, TvFlowIntent.requestedPerson("un film d'action avec du suspense"))
    }
    @Test fun actorCreditsRequireExactMovieAndCompatibleYear() {
        assertTrue(TvFlowIntent.sameMovie("Mission Impossible", 1996, "FR - Mission Impossible (1996)", 1996))
        assertFalse(TvFlowIntent.sameMovie("Mission Impossible", 1996, "Mission Impossible 2", 2000))
        assertFalse(TvFlowIntent.sameMovie("Tom Cruise", null, "Tom & Jerry (2021)", 2021))
    }
    @Test fun computingTopicExpandsSearchWithoutAcceptingUnrelatedSeries() {
        val terms = TvFlowIntent.searchTerms("trouve une série sur l'informatique")
        assertTrue(terms.contains("hacker"))
        assertTrue(terms.contains("cyber"))
        assertTrue(terms.contains("programmation"))
        val query = TvFlowIntent.parse("série sur l'informatique")
        assertTrue(TvFlowIntent.score(query, "Hackers", "Un groupe de hackers", null) > 0)
        assertTrue(TvFlowIntent.score(query, "Cyber War", "Des spécialistes de cybersécurité", null) > 0)
        assertTrue(TvFlowIntent.score(query, "Robot", "Un programmeur lutte contre des pirates informatiques", null) > 0)
        assertEquals(0, TvFlowIntent.score(query, "Cuisine et voyage", "Recettes et découvertes", null))
    }
    @Test fun emptyRequestCannotReturnInventedRecommendations() {
        val intent = TvFlowIntent.parse("je veux un film")
        assertTrue(intent.terms.isEmpty())
        assertFalse(intent.seriesOnly)
    }
    @Test fun multilingualTitlesAndAccentInsensitiveGenres() {
        val french = TvFlowIntent.parse("Comédie française")
        assertTrue(TvFlowIntent.score(french, "Comedie francaise", null, 2023) > 0)
        assertEquals(0, TvFlowIntent.score(french, "English thriller", null, 2023))
        val scienceFiction = TvFlowIntent.parse("Film extraterrestre")
        assertTrue(TvFlowIntent.score(scienceFiction, "Alien encounter", null, 2022) > 0)
        val detective = TvFlowIntent.parse("Série policière")
        assertTrue(TvFlowIntent.score(detective, "Police investigation", null, 2021) > 0)
    }
    @Test fun actorNameParsingDoesNotAssumeRandomGenreIsAnActor() {
        assertEquals("leonardo dicaprio", TvFlowIntent.requestedPerson("Film avec Leonardo DiCaprio"))
        assertEquals(null, TvFlowIntent.requestedPerson("film avec du suspense"))
    }
    @Test fun recognizesEverydayFrenchGenreRequests() {
        val parsed = TvFlowIntent.parse("Une comedie francaise")
        assertTrue(parsed.terms.contains("comedie"))
        assertTrue(parsed.terms.contains("francais"))
        assertTrue(TvFlowIntent.searchTerms("Une comedie francaise").contains("francais"))
    }
}
