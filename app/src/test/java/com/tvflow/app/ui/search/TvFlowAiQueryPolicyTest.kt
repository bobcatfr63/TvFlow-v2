package com.tvflow.app.ui.search

import org.junit.Assert.*
import org.junit.Test

class TvFlowAiQueryPolicyTest {
    @Test fun freeQuotaReservedForComplexQueries() {
        assertFalse(TvFlowAiQueryPolicy.needsGroq("Une comédie française"))
        assertFalse(TvFlowAiQueryPolicy.needsGroq("un film d'action récent"))
        assertTrue(TvFlowAiQueryPolicy.needsGroq("Je veux un thriller avec une ambiance sombre et une fin surprenante"))
        assertTrue(TvFlowAiQueryPolicy.needsGroq("Une série qui parle d'une famille avec beaucoup de secrets"))
    }
    @Test fun unavailableGroqPreservesLocalLookups() {
        val raw = "une série policière française de 2025"
        assertEquals(TvFlowIntent.searchTerms(raw).take(8), TvFlowAiQueryPolicy.searchTerms(raw, null))
    }
    @Test fun inventedAiTitlesCannotRemoveOriginalTerms() {
        val raw = "film action aventure 2025"
        assertTrue(TvFlowAiQueryPolicy.searchTerms(raw, "Titre inventé").containsAll(TvFlowIntent.searchTerms(raw)))
        val parsed = TvFlowIntent.parse(raw)
        assertEquals(2025, parsed.requestedYear)
        assertEquals(0, TvFlowIntent.score(parsed, "Titre inventé", null, 2024, null, "35"))
    }
    @Test fun hintsAreBounded() {
        assertTrue(TvFlowAiQueryPolicy.searchTerms("film action", "un deux trois quatre cinq six sept huit neuf").size <= 8)
    }
    @Test fun productionEndpointRejectsCleartextAndCredentialUrls() {
        assertFalse(AiGatewayConfig.validEndpoint("http://example.org/v1/intent", false))
        assertFalse(AiGatewayConfig.validEndpoint("https://example.org:99999/v1/intent", false))
        assertFalse(AiGatewayConfig.validEndpoint("https://user:pass@example.org/v1/intent", false))
        assertFalse(AiGatewayConfig.validEndpoint("https://example.org/v1/intent?key=secret", false))
        assertFalse(AiGatewayConfig.validEndpoint("https://example.org/v1/intent#secret", false))
        assertTrue(AiGatewayConfig.validEndpoint("https://example.org/v1/intent", false))
        assertTrue(AiGatewayConfig.validEndpoint("http://127.0.0.1:8765/v1/intent", true))
        assertFalse(AiGatewayConfig.validEndpoint("http://127.0.0.1:8765/v1/intent", false))
    }
}
