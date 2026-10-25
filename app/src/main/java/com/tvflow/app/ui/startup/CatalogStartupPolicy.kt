package com.tvflow.app.ui.startup

/**
 * Startup navigation must not depend on background image prefetch completion.
 * A failed initial sync allows access to settings and retry actions.
 */
internal fun shouldOpenCatalog(
    introFinished: Boolean,
    loggedIn: Boolean,
    catalogStatus: CatalogStatus,
    skippedLoading: Boolean
): Boolean = introFinished && loggedIn && (
    skippedLoading || catalogStatus == CatalogStatus.Ready || catalogStatus == CatalogStatus.Failed
)
