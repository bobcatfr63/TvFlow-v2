package com.tvflow.app.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Network-aware initial buffering for Media3. Existing FAST/BALANCED/SAFE remain manual.
 * Media3 continues adapting bitrate and playback internally. Does not apply to libVLC.
 */
internal fun autoBufferDurations(context: Context): IntArray {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val capabilities = runCatching { manager?.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
    val metered = runCatching { manager?.isActiveNetworkMetered ?: true }.getOrDefault(true)
    val local = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true ||
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val lowMemory = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
        ?.isLowRamDevice == true

    return when {
        !local || metered -> intArrayOf(8_000, 30_000, 2_000, 3_500)
        lowMemory || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
            intArrayOf(5_000, 20_000, 1_200, 2_000)
        else -> intArrayOf(3_000, 15_000, 900, 1_500)
    }
}
