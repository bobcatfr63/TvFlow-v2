package com.tvflow.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.SystemClock
import com.tvflow.app.MainActivity
import com.tvflow.app.data.local.SettingsStore
import com.tvflow.app.util.isTelevisionUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BootLiveTarget(
    val streamId: Int,
    val name: String,
    val categoryId: String
)

class TvBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!context.isTelevisionUi()) return

        val startMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "BOOT_COMPLETED received")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsStore(context.applicationContext)
                val prefs = store.playerPreferences.first()
                if (!prefs.autoStartOnTvBoot) return@launch

                val lastLive = if (prefs.resumeLastLiveOnTvBoot) {
                    store.lastLiveChannel.first()
                } else {
                    null
                }

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_STARTED_FROM_TV_BOOT, true)
                    lastLive?.let {
                        putExtra(EXTRA_BOOT_STREAM_ID, it.streamId)
                        putExtra(EXTRA_BOOT_CHANNEL_NAME, it.name)
                        putExtra(EXTRA_BOOT_CATEGORY_ID, it.categoryId)
                    }
                }
                context.startActivity(launchIntent)
                Log.i(TAG, "TVFlow auto-start requested in ${SystemClock.elapsedRealtime() - startMs}ms; resume=" + (lastLive != null))
            } catch (t: Throwable) {
                Log.e(TAG, "TVFlow auto-start failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_STARTED_FROM_TV_BOOT = "tvflow_started_from_tv_boot"
        const val EXTRA_BOOT_STREAM_ID = "tvflow_boot_stream_id"
        const val EXTRA_BOOT_CHANNEL_NAME = "tvflow_boot_channel_name"
        const val EXTRA_BOOT_CATEGORY_ID = "tvflow_boot_category_id"
        private const val TAG = "TvBootReceiver"
    }
}
