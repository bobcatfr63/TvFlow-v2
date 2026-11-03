package com.tvflow.app.ui.common

import android.app.Activity
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.tvflow.app.BuildConfig
import com.tvflow.app.util.isTelevisionUi

/** One orientation owner for a Next phone playback session, including adjacent episodes. */
@Composable
internal fun NextPlaybackOrientation(playback: Boolean, routeResolved: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity
    val enabled = BuildConfig.APPLICATION_ID.endsWith(".next") && !context.isTelevisionUi()
    var captured by rememberSaveable { mutableStateOf(false) }
    var initial by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    var initialDisplay by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT) }
    // Android can remember the forced player rotation even when rotation is locked.
    // Restore the entry display direction first, then release to the original policy.
    // This coroutine is cancelled if another playback route is entered meanwhile.
    LaunchedEffect(activity, enabled, playback, routeResolved) {
        if (enabled && activity != null && routeResolved && !playback && captured) {
            activity.requestedOrientation = initialDisplay
            delay(500)
            activity.requestedOrientation = initial
            captured = false
            Log.i("TVFlowOrientation", "Entry display restored; policy=$initial")
        }
    }
    DisposableEffect(activity, enabled, playback, routeResolved) {
        if (enabled && activity != null && routeResolved && playback) {
            if (!captured) {
                initialDisplay = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                initial = activity.requestedOrientation
                captured = true
            }
            val restoreTo = initial
            // USER_LANDSCAPE uses sensors only when the user enables auto-rotation.
            // SENSOR_LANDSCAPE can change the remembered locked rotation during playback.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            Log.i("TVFlowOrientation", "Playback landscape; restoreTo=$restoreTo")
            onDispose {
                activity.requestedOrientation = restoreTo
                Log.i("TVFlowOrientation", "Playback orientation restored=$restoreTo")
            }
        } else {
            // NavHost can briefly have no route while restoring an Activity. Keep the saved session.
            // The return coroutine clears the session after the entry display has settled.
            onDispose { }
        }
    }
}
