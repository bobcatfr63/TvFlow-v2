package com.tvflow.app

import android.app.Application
import com.tvflow.app.connect.TvFlowConnect
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.launcher.TvLauncherMode
import com.tvflow.app.util.isTelevisionUi
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class TVFlowApplication : Application() {
    @Inject lateinit var settingsStore: SettingsStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        TvFlowConnect.startReceiverIfTv(this)

        if (isTelevisionUi()) {
            appScope.launch {
                val enabled = settingsStore.playerPreferences.first().tvLauncherEnabled
                TvLauncherMode.setEnabled(this@TVFlowApplication, enabled)
            }
        }
    }
}
