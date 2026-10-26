package com.tvflow.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.tvflow.app.boot.BootLiveTarget
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.data.repository.selectTntChannels
import com.tvflow.app.ui.guide.GUIDE_TNT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class InstantTvResolver @Inject constructor(
    private val repository: XtreamRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    suspend fun resolve(): BootLiveTarget? {
        if (!settingsStore.playerPreferences.first().resumeLiveOnAppLaunch) return null
        val last = settingsStore.lastLiveChannel.first()
        val channels = withTimeoutOrNull(2_500L) {
            repository.observeAllChannels().first { it.isNotEmpty() }
        } ?: return null
        val tnt = selectTntChannels(channels)
        val selected = tnt.firstOrNull { it.streamId == last?.streamId } ?: tnt.firstOrNull()
        return selected?.let { BootLiveTarget(it.streamId, it.name, GUIDE_TNT) }
    }
}
