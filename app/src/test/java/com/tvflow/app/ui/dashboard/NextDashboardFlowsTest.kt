package com.tvflow.app.ui.dashboard

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.assertEquals
import org.junit.Test

class NextDashboardFlowsTest {
    private suspend fun until(condition: () -> Boolean) = withTimeout(3_000) {
        while (!condition()) yield()
    }

    @Test fun noQueryStartsUntilTheDashboardIsObserved() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var starts = 0
        try {
            flow { starts++; emit(1); awaitCancellation() }.shareWhileDashboardObserved(scope)
            yield()
            assertEquals(0, starts)
        } finally { scope.cancel() }
    }

    @Test fun railsAndHeroShareQueriesAndUpdatesUntilTheLastConsumerLeaves() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val input = Channel<Int>(Channel.UNLIMITED)
        val rail = Channel<Int>(Channel.UNLIMITED)
        val hero = Channel<Int>(Channel.UNLIMITED)
        var starts = 0
        var stops = 0
        try {
            val shared = flow {
                starts++
                try { for (value in input) emit(value) } finally { stops++ }
            }.shareWhileDashboardObserved(scope)
            val a = scope.launch { shared.collect { rail.send(it) } }
            val b = scope.launch { shared.collect { hero.send(it) } }
            input.send(1)
            assertEquals(1, withTimeout(3_000) { rail.receive() })
            assertEquals(1, withTimeout(3_000) { hero.receive() })
            assertEquals(1, starts)
            input.send(2)
            assertEquals(2, withTimeout(3_000) { rail.receive() })
            assertEquals(2, withTimeout(3_000) { hero.receive() })
            a.cancelAndJoin()
            assertEquals(0, stops)
            input.send(3)
            assertEquals(3, withTimeout(3_000) { hero.receive() })
            b.cancelAndJoin()
            until { stops == 1 }
        } finally { scope.cancel() }
    }

    @Test fun returningAfterStopDoesNotReplayAnObsoleteProfileResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val seen = Channel<Int>(Channel.UNLIMITED)
        var starts = 0
        var stops = 0
        try {
            val shared = flow {
                starts++
                try { emit(starts); awaitCancellation() } finally { stops++ }
            }.shareWhileDashboardObserved(scope)
            val first = scope.launch { shared.collect { seen.send(it) } }
            assertEquals(1, withTimeout(3_000) { seen.receive() })
            first.cancelAndJoin()
            until { stops == 1 }
            yield()
            val second = scope.launch { shared.collect { seen.send(it) } }
            assertEquals(2, withTimeout(3_000) { seen.receive() })
            second.cancelAndJoin()
            until { stops == 2 }
        } finally { scope.cancel() }
    }

    @Test fun clearingTheViewModelScopeCancelsAnInFlightQuery() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var started = false
        var cancelled = false
        try {
            val shared = flow<Int> {
                started = true
                try { awaitCancellation() } finally { cancelled = true }
            }.shareWhileDashboardObserved(scope)
            scope.launch { shared.collect() }
            until { started }
            scope.cancel()
            until { cancelled }
        } finally { scope.cancel() }
    }
}
