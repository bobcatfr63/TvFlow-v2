package com.tvflow.app.ui.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStartupPolicyTest {
    @Test fun waitsForInitialSynchronization() {
        assertFalse(shouldOpenCatalog(true, true, CatalogStatus.Checking, false))
        assertFalse(shouldOpenCatalog(true, true, CatalogStatus.Syncing, false))
    }

    @Test fun opensOnSuccessFailureOrSkip() {
        assertTrue(shouldOpenCatalog(true, true, CatalogStatus.Ready, false))
        assertTrue(shouldOpenCatalog(true, true, CatalogStatus.Failed, false))
        assertTrue(shouldOpenCatalog(true, true, CatalogStatus.Syncing, true))
    }

    @Test fun neverOpensBeforeIntroOrWithoutAuthentication() {
        assertFalse(shouldOpenCatalog(false, true, CatalogStatus.Ready, true))
        assertFalse(shouldOpenCatalog(true, false, CatalogStatus.Ready, true))
    }
}
