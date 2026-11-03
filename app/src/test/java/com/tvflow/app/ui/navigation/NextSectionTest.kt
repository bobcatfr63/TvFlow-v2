package com.tvflow.app.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class NextSectionTest {
    @Test fun everyConcreteSectionRouteResolvesToItsOwnTab() {
        NextSection.entries.forEach { assertEquals(it, nextSectionForRoute(it.route)) }
        assertEquals(NextSection.entries.size, NextSection.entries.map { it.route }.distinct().size)
    }
    @Test fun libraryTemplateUsesItsArgumentRatherThanSelectingBothTabs() {
        assertEquals(NextSection.Movies, nextSectionForRoute("library/{type}", "VOD"))
        assertEquals(NextSection.Series, nextSectionForRoute("library/{type}", "SERIES"))
        assertNull(nextSectionForRoute("library/{type}", "LIVE"))
        assertNull(nextSectionForRoute("library/{type}"))
    }
    @Test fun playbackDetailsAndLoginDoNotGainASectionBar() {
        listOf("startup", "login", "addProfile", "vodPlayer/vod/12/title/mp4", "series/{seriesId}/{name}?categoryId={categoryId}", "player/{streamId}/{name}/{categoryId}?instantTv={instantTv}").forEach {
            assertNull(nextSectionForRoute(it))
        }
    }
    @Test fun localSearchAndAiHaveDifferentDestinations() {
        assertNotEquals(NextSection.Search.route, NextSection.Ai.route)
        assertEquals(NextSection.Search, nextSectionForRoute("search"))
        assertEquals(NextSection.Ai, nextSectionForRoute("tvflowAi"))
    }
}
