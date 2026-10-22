package com.tvflow.app.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFlowAiLocaleRankingTest {
    @Test fun prioritizesFrenchProviderCategoriesOnlyWhenImplicit() {
        assertEquals(6, TvFlowAiLocaleRanking.frenchBonus("VOD - ACTION [FR]", true))
        assertEquals(6, TvFlowAiLocaleRanking.frenchBonus("FR - COMEDIE", true))
        assertEquals(0, TvFlowAiLocaleRanking.frenchBonus("VOD - ACTION [EN]", true))
        assertEquals(0, TvFlowAiLocaleRanking.frenchBonus("VOD - ACTION [FR]", false))
    }

    @Test fun internationalRequestsDisableFrenchPreference() {
        assertTrue(TvFlowAiLocaleRanking.requestsInternational("Un film américain"))
        assertTrue(TvFlowAiLocaleRanking.requestsInternational("Des séries étrangères"))
        assertTrue(TvFlowAiLocaleRanking.requestsInternational("Films en anglais"))
        assertFalse(TvFlowAiLocaleRanking.requestsInternational("Une comédie française"))
    }
}
