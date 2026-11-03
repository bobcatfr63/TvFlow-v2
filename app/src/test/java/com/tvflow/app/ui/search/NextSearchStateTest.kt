package com.tvflow.app.ui.search

import com.tvflow.app.data.repository.GlobalSearchResults
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class NextSearchStateTest {
    @Test fun shortAndBlankQueriesDoNotSearch() = runBlocking {
        var calls = 0
        val states = nextSearchStates(flowOf("", " ", "a")) { calls++; GlobalSearchResults() }.toList()
        assertEquals(0, calls)
        assertFalse(states.last().loading)
        assertNull(states.last().error)
    }
    @Test fun loadingAndFailuresAreDifferentFromAnEmptySuccessfulResult() = runBlocking {
        val failed = nextSearchStates(flowOf("  film  ")) { throw IOException("private-detail") }.toList()
        assertTrue(failed.first().loading)
        assertEquals("film", failed.last().query)
        assertFalse(failed.last().loading)
        assertNotNull(failed.last().error)
        assertFalse(failed.last().error!!.contains("private-detail"))
        val empty = nextSearchStates(flowOf("film")) { GlobalSearchResults() }.toList()
        assertNull(empty.last().error)
        assertFalse(empty.last().loading)
    }
    @Test fun aNewQueryCancelsThePreviousSearchBeforePublishingNewResults() = runBlocking {
        val queries = MutableStateFlow("alpha")
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val result = async {
            nextSearchStates(queries) { query ->
                calls += query
                if (query == "alpha") {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                GlobalSearchResults()
            }.first { it.query == "beta" && !it.loading }
        }
        withTimeout(3_000) { entered.await() }
        queries.value = "beta"
        assertEquals("beta", withTimeout(3_000) { result.await() }.query)
        assertTrue(cancelled.isCompleted)
        assertEquals(listOf("alpha", "beta"), calls)
    }
    @Test fun cancellationDoesNotPublishAFailureResult() = runBlocking {
        // flatMapLatest treats cancellation of its inner flow as normal completion.
        val states = nextSearchStates(flowOf("film")) {
            throw CancellationException("cancelled")
        }.toList()
        assertEquals(1, states.size)
        assertTrue(states.single().loading)
        assertNull(states.single().error)
    }
}
