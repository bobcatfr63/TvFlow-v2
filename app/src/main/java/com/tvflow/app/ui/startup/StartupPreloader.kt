package com.tvflow.app.ui.startup

import android.content.Context
import android.util.Log
import android.os.SystemClock
import coil3.imageLoader
import coil3.request.ImageRequest
import com.tvflow.app.data.local.TVFlowDao
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped best-effort cache warming. Continues after the intro ViewModel disappears.
 * A missing Live catalog triggers one bounded, background initial sync per process.
 * Never synchronizes the full VOD/series catalog on startup or blocks navigation.
 */
@Singleton
class StartupPreloader @Inject constructor(
    private val repository: XtreamRepository,
    private val dao: TVFlowDao,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false
    @Volatile private var initialSyncAttempted = false

    fun start() {
        synchronized(this) {
            if (running) return
            running = true
        }
        scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i("TVFlowStartup", "Prefetch started")
            try {
                // A restored profile may have no local catalog: fetch Live once, never repeatedly
                // reload the entire VOD/series catalog during app startup.
                val profileId = repository.activeProfileId.first()
                if (profileId != null && dao.observeManageCategories(profileId).first().isEmpty()
                    && !initialSyncAttempted) {
                    initialSyncAttempted = true
                    Log.i("TVFlowStartup", "Empty restored Live catalog; initial synchronization started")
                    try {
                        withTimeout(90_000L) { repository.refreshCatalog() }
                        Log.i("TVFlowStartup", "Initial Live catalog synchronization completed")
                    } catch (cancelled: CancellationException) {
                        Log.w("TVFlowStartup", "Initial Live catalog synchronization timed out or cancelled")
                    } catch (error: Exception) {
                        Log.e("TVFlowStartup", "Initial Live catalog synchronization failed", error)
                    }
                }
                withTimeout(12_000L) { warm() }
            } catch (cancelled: CancellationException) {
                // A time limit is expected; never hold up startup.
                Log.d("TVFlowStartup", "Prefetch finished or timed out")
            } catch (error: Exception) {
                Log.w("TVFlowStartup", "Prefetch skipped", error)
            } finally {
                Log.i("TVFlowStartup", "Prefetch elapsed=${SystemClock.elapsedRealtime() - startedAt}ms")
                running = false
            }
        }
    }

    private suspend fun warm() {
        val profileId = repository.activeProfileId.first() ?: return
        val liveIds = repository.observeCategories().first().filterNot { it.isLocked }.map { it.id }
        val vodIds = repository.observeMediaCategories(MediaType.VOD).first().map { it.id }
        val seriesIds = repository.observeMediaCategories(MediaType.SERIES).first().map { it.id }
        Log.i("TVFlowStartup", "Visible categories: live=${liveIds.size}, vod=${vodIds.size}, series=${seriesIds.size}")
        val images = mutableListOf<String>()
        liveIds.take(8).forEach { id ->
            dao.getStartupChannels(profileId, id, 12).mapNotNullTo(images) { it.logoUrl }
        }
        vodIds.take(8).forEach { id ->
            dao.getVodAiCategoryCandidates(profileId, id, 12).mapNotNullTo(images) { it.posterUrl }
        }
        seriesIds.take(8).forEach { id ->
            dao.getSeriesAiCategoryCandidates(profileId, id, 12).mapNotNullTo(images) { it.coverUrl }
        }
        // Only HTTP(S) artwork. Bounded sequential requests, compatible with Coil's UI cache.
        var requested = 0
        var successful = 0
        images.asSequence()
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
            .take(18)
            .forEach { url ->
                try {
                    requested++
                    val result = context.imageLoader.execute(
                        ImageRequest.Builder(context).data(url).size(160, 240).build()
                    )
                    if (result is coil3.request.SuccessResult) successful++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Bad provider artwork should never stop remaining prefetch.
                }
            }
        Log.i("TVFlowStartup", "Prefetch images: requested=$requested successful=$successful")
    }
}
