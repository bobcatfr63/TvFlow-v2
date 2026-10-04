package com.tvflow.app.cast

import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage

@Composable
fun CastRouteButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (context.isTelevisionUi()) return

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val themedContext = ContextThemeWrapper(viewContext, com.tvflow.app.R.style.Theme_TVFlow_CastButton)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
                contentDescription = "Caster"
                isClickable = true
                isFocusable = true
                setOnClickListener { showDialog() }
            }
        }
    )
}

@Composable
fun CastPlaybackEffect(
    url: String?,
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    isLive: Boolean,
    startPositionMs: () -> Long = { 0L },
    onCastActiveChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    if (context.isTelevisionUi()) return

    val currentUrl by rememberUpdatedState(url)
    val currentTitle by rememberUpdatedState(title)
    val currentSubtitle by rememberUpdatedState(subtitle)
    val currentImageUrl by rememberUpdatedState(imageUrl)
    val currentIsLive by rememberUpdatedState(isLive)
    val currentStartPosition by rememberUpdatedState(startPositionMs)
    val currentCastCallback by rememberUpdatedState(onCastActiveChanged)

    val castContext = remember(context) {
        runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
    }
    var connectedSession by remember { mutableStateOf<CastSession?>(null) }
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    fun loadCurrent(session: CastSession) {
        val mediaUrl = currentUrl?.takeIf { it.isNotBlank() } ?: return
        if (loadedUrl == mediaUrl) return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, currentTitle)
            currentSubtitle?.takeIf { it.isNotBlank() }?.let {
                putString(MediaMetadata.KEY_SUBTITLE, it)
            }
            currentImageUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { addImage(WebImage(Uri.parse(it))) } }
        }

        val mediaInfo = MediaInfo.Builder(mediaUrl)
            .setStreamType(
                if (currentIsLive) MediaInfo.STREAM_TYPE_LIVE
                else MediaInfo.STREAM_TYPE_BUFFERED
            )
            .setContentType(castContentType(mediaUrl))
            .setMetadata(metadata)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(if (currentIsLive) 0L else currentStartPosition().coerceAtLeast(0L))
            .build()

        session.remoteMediaClient?.load(request)
        loadedUrl = mediaUrl
    }

    DisposableEffect(castContext) {
        val manager = castContext?.sessionManager
        if (manager == null) {
            onDispose { }
        } else {
            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarting(session: CastSession) = Unit

                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    connectedSession = session
                    loadedUrl = null
                    currentCastCallback(true)
                    loadCurrent(session)
                }

                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    connectedSession = null
                    loadedUrl = null
                    currentCastCallback(false)
                }

                override fun onSessionEnding(session: CastSession) = Unit

                override fun onSessionEnded(session: CastSession, error: Int) {
                    connectedSession = null
                    loadedUrl = null
                    currentCastCallback(false)
                }

                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    connectedSession = session
                    loadedUrl = null
                    currentCastCallback(true)
                    loadCurrent(session)
                }

                override fun onSessionResumeFailed(session: CastSession, error: Int) {
                    connectedSession = null
                    loadedUrl = null
                    currentCastCallback(false)
                }

                override fun onSessionSuspended(session: CastSession, reason: Int) {
                    connectedSession = null
                    currentCastCallback(false)
                }
            }

            manager.addSessionManagerListener(listener, CastSession::class.java)
            manager.currentCastSession?.takeIf { it.isConnected }?.let { session ->
                connectedSession = session
                currentCastCallback(true)
                loadCurrent(session)
            }

            onDispose {
                manager.removeSessionManagerListener(listener, CastSession::class.java)
            }
        }
    }

    LaunchedEffect(currentUrl, connectedSession) {
        connectedSession?.takeIf { it.isConnected }?.let {
            loadedUrl = null
            loadCurrent(it)
        }
    }
}

private fun Context.isTelevisionUi(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

private fun castContentType(url: String): String {
    val clean = url.substringBefore('?').lowercase()
    return when {
        clean.endsWith(".m3u8") -> "application/x-mpegURL"
        clean.endsWith(".mpd") -> "application/dash+xml"
        clean.endsWith(".mp4") || clean.endsWith(".m4v") -> "video/mp4"
        clean.endsWith(".mkv") -> "video/x-matroska"
        clean.endsWith(".ts") -> "video/mp2t"
        else -> "video/mp4"
    }
}
