package com.tvflow.app.ui.dashboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/** One Room subscription for rails and hero; stop when both leave the dashboard. */
internal fun <T> Flow<T>.shareWhileDashboardObserved(scope: CoroutineScope): Flow<T> =
    shareIn(
        scope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        replay = 1
    )
