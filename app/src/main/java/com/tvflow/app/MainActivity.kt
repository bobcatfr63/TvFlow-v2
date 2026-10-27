package com.tvflow.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.tvflow.app.boot.BootLiveTarget
import com.tvflow.app.boot.TvBootReceiver
import com.tvflow.app.ui.navigation.TVFlowNavHost
import com.tvflow.app.ui.theme.TVFlowTheme
import com.tvflow.app.ui.theme.ThemeViewModel
import com.tvflow.app.util.isTelevisionUi
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityStart = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        Log.i("TVFlowStartup", "Activity super.onCreate ${SystemClock.uptimeMillis() - activityStart}ms")
        if (!isTelevisionUi()) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        enableEdgeToEdge()
        if (isTelevisionUi()) applyImmersiveMode()
        requestNotificationPermissionIfNeeded()

        val bootLiveTarget = intent
            ?.takeIf { it.getBooleanExtra(TvBootReceiver.EXTRA_STARTED_FROM_TV_BOOT, false) }
            ?.let { bootIntent ->
                val streamId = bootIntent.getIntExtra(TvBootReceiver.EXTRA_BOOT_STREAM_ID, -1)
                val name = bootIntent.getStringExtra(TvBootReceiver.EXTRA_BOOT_CHANNEL_NAME).orEmpty()
                val categoryId = bootIntent.getStringExtra(TvBootReceiver.EXTRA_BOOT_CATEGORY_ID).orEmpty()
                if (streamId > 0 && name.isNotBlank()) {
                    BootLiveTarget(streamId, name, categoryId)
                } else {
                    null
                }
            }

        Log.i("TVFlowStartup", "Activity before setContent ${SystemClock.uptimeMillis() - activityStart}ms")
        var compositionLogged = false
        setContent {
            val accent by themeViewModel.accent.collectAsState()
            var atmosphereSuppressed by androidx.compose.runtime.remember { mutableStateOf(true) }
            TVFlowTheme(accent = accent, showAtmosphere = !atmosphereSuppressed) {
                TVFlowNavHost(bootLiveTarget = bootLiveTarget, onPlaybackRouteChanged = { atmosphereSuppressed = it })
            }
            SideEffect {
                if (!compositionLogged) {
                    compositionLogged = true
                    Log.i("TVFlowStartup", "First Compose commit ${SystemClock.uptimeMillis() - activityStart}ms")
                }
            }
        }
        Log.i("TVFlowStartup", "Activity after setContent ${SystemClock.uptimeMillis() - activityStart}ms")
        window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                Log.i("TVFlowStartup", "First pre-draw ${SystemClock.uptimeMillis() - activityStart}ms")
                return true
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isTelevisionUi()) applyImmersiveMode()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (isTelevisionUi()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("tvflow_notification_permission", MODE_PRIVATE)
            if (!prefs.getBoolean("asked", false)) {
                prefs.edit().putBoolean("asked", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
