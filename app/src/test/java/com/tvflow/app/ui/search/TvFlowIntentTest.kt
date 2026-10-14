package com.tvflow.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFlowIntentTest {
    @Test fun tmdbGenreIdsImproveCombinedGenreMatches() {
        val query = TvFlowIntent.parse("une série action et aventure")
        assertTrue(TvFlowIntent.score(query, "Série sans mots-clés", null, 2024, null, "10759") > 0)
        assertEquals(0, TvFlowIntent.score(query, "Les aventures", "Un conte", 2024, null, "16"))
        val movie = TvFlowIntent.parse("film action")
        assertTrue(TvFlowIntent.score(movie, "Mission", null, 2024, null, "28") >
            TvFlowIntent.score(movie, "Mission", null, 2024))
    }
    @Test fun combinedGenresUseExplicitProviderCategoryAndDoNotTrustAdventureTitleAlone() {
        val query = TvFlowIntent.parse("une série d'action et d'aventure")
        assertTrue(TvFlowIntent.score(query, "Aucune indication", null, null, "FR Action Aventure") > 0)
        assertEquals(0, TvFlowIntent.score(query, "Les Aventures de Mickey", null, null, "FR Dessins animés"))
        assertEquals(0, TvFlowIntent.score(query, "Les Aventures de Mickey", null, null, "FR Aventure"))
        assertEquals(0, TvFlowIntent.score(query, "Mission Action", "Une aventure", null, "FR Comédie"))
    }
    @Test fun actionAdventureRequiresBothGenresAndHandlesPlural() {
        val request = TvFlowIntent.parse("cherche une série d'actions et aventure")
        assertTrue(request.seriesOnly)
        assertTrue(request.terms.contains("action"))
        assertTrue(request.terms.contains("aventure"))
        assertEquals(0, TvFlowIntent.score(request, "Les aventures de Mickey", "Série jeunesse comique", null))
        assertEquals(0, TvFlowIntent.score(request, "Les aventures de Robin", "Un jeune homme combat le Baron", null))
        assertTrue(TvFlowIntent.score(request, "Mission action", "Une grande aventure à travers le monde", null, "FR Action") > 0)
    }
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
    @Test fun providerCategoriesCanDiscoverGenreTitlesWithoutKeywords() {
        val request = TvFlowIntent.parse("cherche une série d'action et aventure")
        assertTrue(TvFlowIntent.score(request, "", null, null, "FR Action Aventure") > 0)
        assertEquals(0, TvFlowIntent.score(request, "", null, null, "FR Action"))
        assertEquals(0, TvFlowIntent.score(request, "", null, null, "FR Comédie"))
        val comedy = TvFlowIntent.parse("une comédie")
        assertTrue(TvFlowIntent.score(comedy, "", null, null, "FR Comédie") > 0)
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
    @Test fun titleRankingAvoidsSubstringFalsePositives() {
        val query = TvFlowIntent.parse("art")
        assertEquals(0, TvFlowIntent.score(query, "Carton rouge", null, 2025))
        assertTrue(TvFlowIntent.score(query, "Art contemporain", null, 2025) > 0)
    }
    @Test fun acceptsLongerFrenchWordFormsAndRanksTitlesBeforeDescriptions() {
        val query = TvFlowIntent.parse("piratage informatique")
        val titleMatch = TvFlowIntent.score(query, "Piratages informatiques", null, 2025)
        val synopsisMatch = TvFlowIntent.score(query, "Enquête", "Piratages informatiques", 2025)
        assertTrue(titleMatch > synopsisMatch)
        assertEquals(0, TvFlowIntent.score(query, "Cuisine", "Des recettes de famille", 2025))
    }
    @Test fun conversationalPhrasesDoNotPolluteSearchTerms() {
        val terms = TvFlowIntent.searchTerms("Trouve-moi un bon film d'action récent")
        assertEquals(listOf("action"), terms)
        assertTrue(TvFlowIntent.parse("Je veux un bon film d'action récent").recent)
    }
    @Test fun frenchPoliceSeriesIntentIgnoresFillerWords() {
        val intent = TvFlowIntent.parse("Recommande-moi une bonne série policière française")
        assertTrue(intent.seriesOnly)
        assertTrue(intent.terms.contains("police"))
        assertTrue(intent.terms.contains("francais"))
        assertFalse(intent.terms.contains("bonne"))
    }
    @Test fun conversationalRecentRequestIsRecognized() {
        val intent = TvFlowIntent.parse("Un nouveau film")
        assertTrue(intent.recent)
        assertTrue(intent.filmOnly)
        assertEquals(0, TvFlowIntent.score(intent, "Film ancien", null, 1990))
    }
    @Test fun similarMovieReferenceRequiresExplicitComparison() {
        assertEquals("interstellar", TvFlowSimilarity.referenceTitle("Trouve-moi un film comme Interstellar"))
        assertEquals("avatar", TvFlowSimilarity.referenceTitle("un film similaire à Avatar"))
        assertEquals(null, TvFlowSimilarity.referenceTitle("trouve-moi un film de science-fiction"))
    }
    @Test fun similarMovieKeywordsRequireSharedCatalogMetadata() {
        val keywords = TvFlowSimilarity.keywords(
            "Voyage interstellaire, exploration spatiale, planete et aventure. Une exploration spatiale."
        )
        assertTrue(keywords.contains("exploration"))
        assertTrue(TvFlowSimilarity.overlap(keywords, "Exploration interstellaire", "Voyage sur une planete") >= 2)
        assertEquals(0, TvFlowSimilarity.overlap(keywords, "Cuisine", "Recettes et famille"))
        assertEquals(emptyList<String>(), TvFlowSimilarity.keywords(null))
    }
    @Test fun recognizesEverydayFrenchGenreRequests() {
        val parsed = TvFlowIntent.parse("Une comedie francaise")
        assertTrue(parsed.terms.contains("comedie"))
        assertTrue(parsed.terms.contains("francais"))
        assertTrue(TvFlowIntent.searchTerms("Une comedie francaise").contains("francais"))
    }
}
