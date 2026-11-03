package com.tvflow.app.ui.search

import com.tvflow.app.data.repository.GlobalSearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

internal data class NextSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: GlobalSearchResults = GlobalSearchResults(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun nextSearchStates(
    queries: Flow<String>,
    search: suspend (String) -> GlobalSearchResults
): Flow<NextSearchState> = queries.flatMapLatest { raw ->
    flow {
        val query = raw.trim()
        if (query.length < 2) {
            emit(NextSearchState(query = query))
        } else {
            emit(NextSearchState(query = query, loading = true))
            delay(180)
            val result = try {
                NextSearchState(query = query, results = search(query))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                NextSearchState(query = query, error = "Recherche indisponible. Réessayez dans un instant.")
            }
            emit(result)
        }
    }
}
