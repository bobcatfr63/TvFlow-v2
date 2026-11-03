package com.tvflow.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class SearchVisibilityTest {
    @Test fun hiddenMatchesCannotConsumeTheVisibleResultQuota() {
        val rows = (1..500).map { SearchIndexEntry("possession", it, "hidden") } +
            SearchIndexEntry("possession visible", 501, "visible")
        assertEquals(listOf(501), findVisibleSearchIds(rows, "possession", 1, setOf("hidden")))
    }
    @Test fun aWarmIndexHonorsHideThenUnhideWithoutRebuilding() {
        val rows = listOf(SearchIndexEntry("film", 1, "one"), SearchIndexEntry("film", 2, "two"))
        assertEquals(listOf(1, 2), findVisibleSearchIds(rows, "film", 60, emptySet()))
        assertEquals(listOf(2), findVisibleSearchIds(rows, "film", 60, setOf("one")))
        assertEquals(listOf(1, 2), findVisibleSearchIds(rows, "film", 60, emptySet()))
    }
    @Test fun mediaTypePrefixesDoNotHideAnotherTypeWithTheSameCategoryId() {
        val snapshot = SearchVisibility(setOf("VOD:17", "SERIES:23", "VOD:provider:two"))
        assertEquals(setOf("17", "provider:two"), snapshot.hiddenCategories("VOD"))
        assertEquals(setOf("23"), snapshot.hiddenCategories("SERIES"))
        assertTrue(snapshot.hiddenCategories("LIVE").isEmpty())
    }
    @Test fun hiddenLiveChannelsAndCategoriesAreBothExcludedBeforeRanking() {
        val rows = listOf(SearchIndexEntry("film", 1, "hidden"), SearchIndexEntry("film", 2, "visible"), SearchIndexEntry("film", 3, "visible"))
        assertEquals(listOf(3), findVisibleSearchIds(rows, "film", 1, setOf("hidden"), setOf(2)))
    }
    @Test fun matchingRankAndStableTiesArePreserved() {
        val rows = listOf(SearchIndexEntry("xxfilm", 1, "a"), SearchIndexEntry("film a", 2, "a"), SearchIndexEntry("film b", 3, "a"), SearchIndexEntry("zfilm", 4, "a"))
        assertEquals(listOf(2, 3, 4), findVisibleSearchIds(rows, "film", 3, emptySet()))
    }
    @Test fun emptyHiddenSetZeroLimitAndMaximumLimitAreHandled() {
        val rows = (1..200).map { SearchIndexEntry("film", it, "a") }
        assertTrue(findVisibleSearchIds(rows, "film", 0, emptySet()).isEmpty())
        assertTrue(findVisibleSearchIds(rows, "film", -2, emptySet()).isEmpty())
        assertEquals(150, findVisibleSearchIds(rows, "film", 999, emptySet()).size)
        assertTrue(findVisibleSearchIds(rows, "absent", 60, emptySet()).isEmpty())
    }
}
