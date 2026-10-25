package com.tvflow.app.data.repository

/**
 * Some Xtream servers return HTTP 200 with an empty catalog on temporary failures.
 * Do not destroy a previously populated local catalog in that situation.
 */
internal fun shouldReplaceCatalog(previousCount: Int, incomingCount: Int): Boolean =
    incomingCount > 0 || previousCount == 0

internal enum class CatalogRefreshOutcome { COMPLETE, PARTIAL, FAILED }

/** A partially refreshed media catalog must never be reported as fully refreshed. */
internal fun combinedCatalogRefreshOutcome(
    liveSucceeded: Boolean,
    mediaOutcome: CatalogRefreshOutcome
): CatalogRefreshOutcome = when {
    liveSucceeded && mediaOutcome == CatalogRefreshOutcome.COMPLETE -> CatalogRefreshOutcome.COMPLETE
    liveSucceeded || mediaOutcome != CatalogRefreshOutcome.FAILED -> CatalogRefreshOutcome.PARTIAL
    else -> CatalogRefreshOutcome.FAILED
}

internal fun catalogRefreshOutcome(liveSucceeded: Boolean, mediaSucceeded: Boolean): CatalogRefreshOutcome =
    when {
        liveSucceeded && mediaSucceeded -> CatalogRefreshOutcome.COMPLETE
        liveSucceeded || mediaSucceeded -> CatalogRefreshOutcome.PARTIAL
        else -> CatalogRefreshOutcome.FAILED
    }
