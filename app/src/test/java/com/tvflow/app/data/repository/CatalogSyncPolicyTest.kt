package com.tvflow.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncPolicyTest {
    @Test fun preservesExistingCatalogWhenServerReturnsEmpty() {
        assertFalse(shouldReplaceCatalog(previousCount = 50_000, incomingCount = 0))
        assertFalse(shouldReplaceCatalog(previousCount = 1, incomingCount = 0))
    }

    @Test fun permitsInitialEmptyCatalogAndSuccessfulRefresh() {
        assertTrue(shouldReplaceCatalog(previousCount = 0, incomingCount = 0))
        assertTrue(shouldReplaceCatalog(previousCount = 0, incomingCount = 2))
        assertTrue(shouldReplaceCatalog(previousCount = 100, incomingCount = 200))
    }

    @Test fun failedSeriesWithSuccessfulLiveAndVodRemainsPartial() {
        val media = catalogRefreshOutcome(liveSucceeded = true, mediaSucceeded = false)
        org.junit.Assert.assertEquals(CatalogRefreshOutcome.PARTIAL, media)
        org.junit.Assert.assertEquals(
            CatalogRefreshOutcome.PARTIAL,
            combinedCatalogRefreshOutcome(liveSucceeded = true, mediaOutcome = media)
        )
    }

    @Test fun failedLiveWithSuccessfulMediaStillAllowsFirstLogin() {
        org.junit.Assert.assertEquals(
            CatalogRefreshOutcome.PARTIAL,
            combinedCatalogRefreshOutcome(false, CatalogRefreshOutcome.COMPLETE)
        )
        org.junit.Assert.assertEquals(
            CatalogRefreshOutcome.FAILED,
            combinedCatalogRefreshOutcome(false, CatalogRefreshOutcome.FAILED)
        )
    }

    @Test fun partialRefreshIsNotTreatedAsTotalFailure() {
        org.junit.Assert.assertEquals(CatalogRefreshOutcome.COMPLETE, catalogRefreshOutcome(true, true))
        org.junit.Assert.assertEquals(CatalogRefreshOutcome.PARTIAL, catalogRefreshOutcome(true, false))
        org.junit.Assert.assertEquals(CatalogRefreshOutcome.PARTIAL, catalogRefreshOutcome(false, true))
        org.junit.Assert.assertEquals(CatalogRefreshOutcome.FAILED, catalogRefreshOutcome(false, false))
    }
}
