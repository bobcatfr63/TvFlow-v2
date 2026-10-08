package com.tvflow.app

import android.app.Application
import android.util.Log
import com.tvflow.app.connect.FavoriteSync
import com.tvflow.app.connect.TvFlowConnect
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.launcher.TvLauncherMode
import com.tvflow.app.update.UpdateNotificationWorker
import com.tvflow.app.util.isTelevisionUi
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidApp
class TVFlowApplication : Application() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var repository: XtreamRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        TvFlowConnect.startReceiverIfTv(this)
        UpdateNotificationWorker.schedule(this)
        startFavoriteSync()

        if (isTelevisionUi()) {
            appScope.launch {
                val enabled = settingsStore.playerPreferences.first().tvLauncherEnabled
                TvLauncherMode.setEnabled(this@TVFlowApplication, enabled)
            }
        }
    }

    private fun startFavoriteSync() {
        appScope.launch {
            TvFlowConnect.favoriteSync.collectLatest { sync ->
                val activeProfileId = repository.activeProfileId.first()
                if (activeProfileId != sync.profileId) return@collectLatest
                runCatching {
                    repository.applySyncedFavorites(
                        profileId = sync.profileId,
                        streamIds = sync.streamIds,
                        timestamp = sync.updatedAt
                    )
                }.onFailure {
                    Log.w("TVFlowFavoriteSync", "Favorite sync receive failed", it)
                }
            }
        }

        appScope.launch {
            repository.activeProfileId
                .flatMapLatest { profileId ->
                    if (profileId == null) {
                        flowOf(null)
                    } else {
                        repository.observeFavoriteIds().flatMapLatest { ids ->
                            settingsStore.favoriteSyncTimestamp(profileId).flatMapLatest { timestamp ->
                                flowOf(
                                    FavoriteSync(
                                        profileId = profileId,
                                        streamIds = ids,
                                        updatedAt = timestamp
                                    )
                                )
                            }
                        }
                    }
                }
                .collectLatest { sync ->
                    if (sync == null || sync.updatedAt <= 0L) return@collectLatest
                    delay(250L)
                    runCatching {
                        TvFlowConnect.broadcastFavoriteSync(this@TVFlowApplication, sync)
                    }.onFailure {
                        Log.d("TVFlowFavoriteSync", "No peer available for favorite sync: " + it.message)
                    }
                }
        }
    }
}
