package com.tvflow.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.ImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.tvflow.app.BuildConfig
import com.tvflow.app.data.AppUpdateInfo
import com.tvflow.app.data.UpdateDownloadState
import com.tvflow.app.ui.common.iptvMediaSourceFactory
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.PlaybackPosition
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import com.tvflow.app.launcher.TvLauncherMode
import com.tvflow.app.util.isTelevisionUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    onLive: () -> Unit,
    onGuide: () -> Unit,
    onVod: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onResume: (PlaybackPosition) -> Unit,
    onChannel: (LiveChannel) -> Unit,
    onVodItem: (VodItem) -> Unit,
    onSeriesItem: (SeriesItem) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isTv = context.isTelevisionUi()
    val listState = rememberLazyListState()
    var appsMode by remember { mutableStateOf(false) }
    var showMoment by remember { mutableStateOf(false) }
    var showTasteEditor by remember { mutableStateOf(false) }
    val tvApps by produceState<List<TvInstalledApp>>(initialValue = emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadTvApps(context) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val mobilePreviewAllowed = !isTv &&
            state.mobileHomePreviewMode != "OFF" &&
            (state.mobileHomePreviewMode == "ALWAYS" || context.isUnmeteredNetwork()) &&
            listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset < 24

        if ((isTv || mobilePreviewAllowed) && !state.backgroundVideoUrl.isNullOrBlank()) {
            TvVideoBackdrop(
                url = state.backgroundVideoUrl.orEmpty(),
                title = state.backgroundVideoTitle,
                mobile = !isTv
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isTv) 0.18f else 0.24f),
                            MaterialTheme.colorScheme.background.copy(alpha = if (isTv) 0.70f else if (mobilePreviewAllowed) 0.72f else 1f),
                            Color.Black
                        )
                    )
                )
                .background(Color.Black.copy(alpha = if (isTv) 0.28f else 0f))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isTv) 42.dp else 16.dp,
                    vertical = if (isTv) 28.dp else 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp)
        ) {
            item {
                if (isTv) {
                    TvHeroHeader(
                        serverName = state.serverName,
                        onLive = onLive,
                        onGuide = onGuide,
                        onVod = onVod,
                        onSeries = onSeries,
                        onSearch = onSearch,
                        onApps = { appsMode = !appsMode },
                        appsSelected = appsMode,
                        onPulse = { showTasteEditor = true },
                        onMoment = { showMoment = true },
                        onSettings = onSettings
                    )
                } else {
                    HeroHeader(
                        serverName = state.serverName,
                        onLive = onLive,
                        onGuide = onGuide,
                        onVod = onVod,
                        onSeries = onSeries,
                        onSearch = onSearch,
                        onApps = { appsMode = !appsMode },
                        appsSelected = appsMode,
                        onPulse = { showTasteEditor = true },
                        onMoment = { showMoment = true },
                        onSettings = onSettings
                    )
                }
            }

            if (state.tonightPrograms.isNotEmpty()) {
                item {
                    TonightRail(
                        items = state.tonightPrograms,
                        onChannel = onChannel
                    )
                }
            }

            if (state.pulseVod.isNotEmpty()) {
                item {
                    RecentVodRail(
                        items = state.pulseVod,
                        onItem = onVodItem,
                        title = "TVFlow Pulse • Films pour toi",
                        subtitle = pulseSubtitle(state.tasteThemes, state.learnedThemes)
                    )
                }
            }
            if (state.pulseSeries.isNotEmpty()) {
                item {
                    RecentSeriesRail(
                        items = state.pulseSeries,
                        onItem = onSeriesItem,
                        title = "TVFlow Pulse • Séries pour toi"
                    )
                }
            }

            if (state.recentVodReleases.isNotEmpty()) {
                item { RecentVodRail(state.recentVodReleases, onVodItem) }
            }
            if (state.recentSeriesReleases.isNotEmpty()) {
                item { RecentSeriesRail(state.recentSeriesReleases, onSeriesItem) }
            }

            if (appsMode && tvApps.isNotEmpty()) {
                item {
                    TvAppRail(
                        apps = tvApps,
                        onLaunch = { TvLauncherMode.launchApp(context, it.packageName) }
                    )
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item { ContinueRail("Continuer à regarder", state.continueWatching, onResume) }
            }
            if (state.recentlyWatched.isNotEmpty()) {
                item { ContinueRail("Récemment regardé", state.recentlyWatched, onResume) }
            }
            if (state.favorites.isNotEmpty()) {
                item { ChannelRail("★  Mes favoris", state.favorites, onChannel) }
            }
        }

        if (state.showTasteOnboarding) {
            TasteProfilePopup(
                initialThemes = state.tasteThemes,
                learnedThemes = state.learnedThemes,
                firstTime = true,
                onSave = viewModel::saveTasteProfile,
                onDismiss = viewModel::skipTasteProfile
            )
        } else if (showTasteEditor) {
            TasteProfilePopup(
                initialThemes = state.tasteThemes,
                learnedThemes = state.learnedThemes,
                firstTime = false,
                onSave = {
                    viewModel.saveTasteProfile(it)
                    showTasteEditor = false
                },
                onDismiss = { showTasteEditor = false }
            )
        } else if (showMoment) {
            MomentPopup(
                pulseVod = state.pulseVod,
                pulseSeries = state.pulseSeries,
                recentVod = state.recentVodReleases,
                recentSeries = state.recentSeriesReleases,
                onVod = {
                    showMoment = false
                    onVodItem(it)
                },
                onSeries = {
                    showMoment = false
                    onSeriesItem(it)
                },
                onDismiss = { showMoment = false }
            )
        }

        val installedReleaseNotesVersion = state.installedReleaseNotesVersion
        if (
            !state.showTasteOnboarding &&
            !showTasteEditor &&
            !showMoment &&
            installedReleaseNotesVersion != null &&
            state.installedReleaseNotes.isNotEmpty()
        ) {
            InstalledReleaseNotesPopup(
                versionName = installedReleaseNotesVersion,
                notes = state.installedReleaseNotes,
                onDismiss = viewModel::dismissInstalledReleaseNotes
            )
        }
    }
}

@Composable
private fun TvVideoBackdrop(url: String, title: String?, mobile: Boolean = false) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(iptvMediaSourceFactory(context))
            .build()
            .apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    Log.i("TVFlowHome", "Background first frame: ${title.orEmpty()}")
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w("TVFlowHome", "Background playback error: ${error.errorCodeName}", error)
                }
            })
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            seekTo(if (mobile) 30_000L else 60_000L)
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.playWhenReady = false
            player.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.player = player
                }
            },
            update = { it.player = player }
        )

        if (!title.isNullOrBlank()) {
            Text(
                text = if (mobile) "Extrait • $title" else "En fond • $title",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White.copy(alpha = 0.66f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Context.isUnmeteredNetwork(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

private data class TvInstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

private fun loadTvApps(context: Context): List<TvInstalledApp> {
    val packageManager = context.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    )
    val discovered = intents
        .asSequence()
        .flatMap { packageManager.queryIntentActivities(it, PackageManager.MATCH_DEFAULT_ONLY).asSequence() }
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            TvInstalledApp(
                label = it.loadLabel(packageManager).toString().ifBlank { it.activityInfo.packageName },
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(packageManager)
            )
        }
        .toList()

    val preferredPackages = listOf(
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.google.android.youtube.tv",
        "com.spotify.tv.android",
        "com.plexapp.android"
    )
    val preferred = preferredPackages.mapNotNull { packageName ->
        val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return@mapNotNull null
        runCatching {
            val info = packageManager.getApplicationInfo(launchIntent.component?.packageName ?: packageName, 0)
            TvInstalledApp(
                label = packageManager.getApplicationLabel(info).toString().ifBlank { packageName },
                packageName = packageName,
                icon = packageManager.getApplicationIcon(info)
            )
        }.getOrNull()
    }

    val preferredIds = preferred.map { it.packageName }.toSet()
    return preferred + discovered
        .filterNot { it.packageName in preferredIds }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun TvHeroHeader(
    serverName: String?,
    onLive: () -> Unit,
    onGuide: () -> Unit,
    onVod: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onApps: () -> Unit,
    appsSelected: Boolean,
    onPulse: () -> Unit,
    onMoment: () -> Unit,
    onSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvTopAction("⌕  Rechercher", onSearch)
            TvTopSelected("Accueil")
            TvTopAction("Direct", onLive)
            TvTopAction("Guide TV", onGuide)
            TvTopAction("Films", onVod)
            TvTopAction("Séries", onSeries)
            TvTopAction("✦  Mon moment", onMoment)
            TvTopAction("Pulse", onPulse)
            if (appsSelected) TvTopSelected("Apps") else TvTopAction("Apps", onApps)
            TvTopAction("⚙  Réglages", onSettings)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    "TVFlow",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    "● Connecté",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                serverName?.takeUnless { it.equals("ok", true) || it.equals("true", true) }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f))
                }
            }
            Text(
                "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.align(Alignment.TopEnd),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun TvTopSelected(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun TvTopAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun TvAppRail(
    apps: List<TvInstalledApp>,
    onLaunch: (TvInstalledApp) -> Unit
) {
    Column {
        SectionTitle("Mes applications")
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps, key = { it.packageName }) { app ->
                TvAppCard(app, onLaunch)
            }
        }
    }
}

@Composable
private fun TvAppCard(app: TvInstalledApp, onLaunch: (TvInstalledApp) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(150.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.surface
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onLaunch(app) }
            .focusable(interactionSource = interaction)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AndroidView(
            modifier = Modifier.width(64.dp).height(52.dp),
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageDrawable(app.icon)
                }
            },
            update = { it.setImageDrawable(app.icon) }
        )
        Text(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HeroHeader(
    serverName: String?,
    onLive: () -> Unit,
    onGuide: () -> Unit,
    onVod: () -> Unit,
    onSeries: () -> Unit,
    onSearch: () -> Unit,
    onApps: () -> Unit,
    appsSelected: Boolean,
    onPulse: () -> Unit,
    onMoment: () -> Unit,
    onSettings: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TVFlow",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "● Connecté",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                serverName?.takeUnless { it.equals("ok", true) || it.equals("true", true) }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f)
                    )
                }
            }
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MobileFeatureTile("●", "Direct", onLive, primary = true)
            MobileFeatureTile("▦", "Guide TV", onGuide)
            MobileFeatureTile("▶", "Films", onVod)
            MobileFeatureTile("S", "Séries", onSeries)
            MobileFeatureTile("✦", "Mon moment", onMoment, primary = true)
            MobileFeatureTile("P", "Pulse", onPulse)
            MobileFeatureTile("⌕", "Recherche", onSearch)
            MobileFeatureTile("▦", if (appsSelected) "Apps ✓" else "Apps", onApps)
            MobileFeatureTile("⚙", "Réglages", onSettings)
        }
    }
}

@Composable
private fun MobileFeatureTile(
    glyph: String,
    title: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(
        targetValue = when {
            focused -> MaterialTheme.colorScheme.onBackground
            primary -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface
        },
        label = "dashboardTile"
    )
    val fg = if (focused) MaterialTheme.colorScheme.background
        else if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .width(126.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleLarge,
            color = fg,
            maxLines = 1
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun UpdatePopup(
    update: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val sizeMb = update.artifactSize / (1024f * 1024f)
    AlertDialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) onDismiss()
        },
        title = {
            Text(
                "Mise à jour TVFlow disponible",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Nouvelle version : ${update.versionName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Version installée : ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Taille : %.1f Mo".format(Locale.getDefault(), sizeMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                update.publishedAt?.takeIf { it.isNotBlank() }?.let { published ->
                    Text(
                        "Publication : $published",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (update.releaseNotes.isNotEmpty()) {
                    Text(
                        "Cette mise à jour apporte :",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    update.releaseNotes.forEach { note ->
                        Text(
                            "• $note",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { downloadState.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Téléchargement : ${downloadState.percent} %")
                    }
                    is UpdateDownloadState.Ready -> Text(
                        "APK vérifié et prêt à être installé.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    is UpdateDownloadState.Error -> Text(
                        downloadState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    UpdateDownloadState.Idle -> Text(
                        "Tu peux installer maintenant ou choisir Plus tard.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            UpdateActionButton(
                text = when (downloadState) {
                    is UpdateDownloadState.Downloading -> "Téléchargement…"
                    is UpdateDownloadState.Ready -> "Installer"
                    is UpdateDownloadState.Error -> "Réessayer"
                    UpdateDownloadState.Idle -> "Mettre à jour"
                },
                enabled = downloadState !is UpdateDownloadState.Downloading,
                primary = true,
                onClick = onUpdate
            )
        },
        dismissButton = {
            UpdateActionButton(
                text = "Plus tard",
                enabled = downloadState !is UpdateDownloadState.Downloading,
                primary = false,
                onClick = onDismiss
            )
        }
    )
}

private val TVFLOW_TASTE_THEMES = listOf(
    "Action", "Aventure", "Comédie", "Thriller",
    "Science-fiction", "Fantastique", "Romance", "Policier",
    "Horreur", "Documentaire", "Animation", "Famille"
)

@Composable
private fun TasteProfilePopup(
    initialThemes: Set<String>,
    learnedThemes: List<String>,
    firstTime: Boolean,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionUi()
    var selectedThemes by remember(initialThemes) { mutableStateOf(initialThemes) }
    val columns = if (isTv) 4 else 2
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!firstTime) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (firstTime) "Bienvenue dans TVFlow Pulse" else "Mon profil Pulse", fontWeight = FontWeight.Bold)
                Text(
                    if (firstTime) "Donne-lui une étincelle. Il apprendra ensuite tout seul."
                    else "Ajuste tes goûts. Pulse garde aussi ce qu’il a appris de tes habitudes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isTv) 500.dp else 440.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Choisis les univers qui te ressemblent. Ces choix pèsent plus fort, puis TVFlow affine les recommandations à partir de ce que tu regardes vraiment.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TVFLOW_TASTE_THEMES.chunked(columns).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            TasteChoiceChip(
                                label = theme,
                                selected = theme in selectedThemes,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedThemes = if (theme in selectedThemes) {
                                        selectedThemes - theme
                                    } else {
                                        selectedThemes + theme
                                    }
                                }
                            )
                        }
                        repeat(columns - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (learnedThemes.isNotEmpty()) {
                    Text(
                        "Pulse a déjà repéré : " + learnedThemes.take(5).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "L’apprentissage reste local à TVFlow. Tu peux revenir ici à tout moment depuis l’accueil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            UpdateActionButton(
                text = when {
                    firstTime && selectedThemes.isEmpty() -> "Commencer sans préférence"
                    firstTime -> "Créer mon Pulse"
                    else -> "Enregistrer"
                },
                enabled = true,
                primary = true,
                onClick = { onSave(selectedThemes) }
            )
        },
        dismissButton = {
            UpdateActionButton(
                text = if (firstTime) "Pas maintenant" else "Fermer",
                enabled = true,
                primary = false,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun TasteChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primaryContainer
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                focused -> MaterialTheme.colorScheme.onPrimaryContainer
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}


private data class MomentCandidate(
    val vod: VodItem? = null,
    val series: SeriesItem? = null,
    val reason: String
)

private val MOMENT_DURATIONS = listOf("20 min", "45 min", "1 h 30", "Soirée", "Surprends-moi")
private val MOMENT_MOODS = listOf("Tout", "Action", "Détente", "Frissons", "Romance", "Famille")

@Composable
private fun MomentPopup(
    pulseVod: List<VodItem>,
    pulseSeries: List<SeriesItem>,
    recentVod: List<VodItem>,
    recentSeries: List<SeriesItem>,
    onVod: (VodItem) -> Unit,
    onSeries: (SeriesItem) -> Unit,
    onDismiss: () -> Unit
) {
    val isTv = LocalContext.current.isTelevisionUi()
    var duration by remember { mutableStateOf("45 min") }
    var mood by remember { mutableStateOf("Tout") }
    var shuffleSeed by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()

    val candidates = remember(
        duration,
        mood,
        shuffleSeed,
        pulseVod,
        pulseSeries,
        recentVod,
        recentSeries
    ) {
        buildMomentCandidates(
            duration = duration,
            mood = mood,
            pulseVod = pulseVod,
            pulseSeries = pulseSeries,
            recentVod = recentVod,
            recentSeries = recentSeries
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("✦ Mon moment", fontWeight = FontWeight.Bold)
                Text(
                    "Dis-moi combien de temps tu as et l’ambiance du moment. TVFlow compose la suite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isTv) 540.dp else 470.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("J’ai…", fontWeight = FontWeight.SemiBold)
                AdaptiveChoiceRows(
                    values = MOMENT_DURATIONS,
                    selected = duration,
                    columns = if (isTv) 5 else 2,
                    onSelected = { duration = it }
                )

                Text("Et j’ai envie de…", fontWeight = FontWeight.SemiBold)
                AdaptiveChoiceRows(
                    values = MOMENT_MOODS,
                    selected = mood,
                    columns = if (isTv) 3 else 2,
                    onSelected = { mood = it }
                )

                if (candidates.isEmpty()) {
                    Text(
                        "Pulse manque encore de données pour composer ce moment. Regarde quelques contenus et il deviendra plus précis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Ton moment TVFlow", fontWeight = FontWeight.Bold)
                    candidates.forEachIndexed { index, candidate ->
                        MomentCandidateRow(
                            candidate = candidate,
                            index = index,
                            onClick = {
                                candidate.vod?.let(onVod)
                                candidate.series?.let(onSeries)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            UpdateActionButton(
                text = "Autre proposition",
                enabled = true,
                primary = true,
                onClick = { shuffleSeed += 1 }
            )
        },
        dismissButton = {
            UpdateActionButton(
                text = "Fermer",
                enabled = true,
                primary = false,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun AdaptiveChoiceRows(
    values: List<String>,
    selected: String,
    columns: Int,
    onSelected: (String) -> Unit
) {
    values.chunked(columns).forEach { rowValues ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowValues.forEach { value ->
                TasteChoiceChip(
                    label = value,
                    selected = value == selected,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(value) }
                )
            }
            repeat(columns - rowValues.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MomentCandidateRow(
    candidate: MomentCandidate,
    index: Int,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val title = candidate.vod?.name ?: candidate.series?.name.orEmpty()
    val image = candidate.vod?.posterUrl ?: candidate.series?.coverUrl
    val kind = if (candidate.vod != null) "Film" else "Série"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(62.dp)
                .height(82.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                if (index == 0) "Choix principal • " + kind else kind + " • Alternative " + (index + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                candidate.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildMomentCandidates(
    duration: String,
    mood: String,
    pulseVod: List<VodItem>,
    pulseSeries: List<SeriesItem>,
    recentVod: List<VodItem>,
    recentSeries: List<SeriesItem>
): List<MomentCandidate> {
    val vodPool = (pulseVod + recentVod).distinctBy { it.streamId }
    val seriesPool = (pulseSeries + recentSeries).distinctBy { it.seriesId }

    fun vodMatches(item: VodItem) = momentMoodMatches(
        mood,
        listOfNotNull(item.name, item.synopsis).joinToString(" ")
    )
    fun seriesMatches(item: SeriesItem) = momentMoodMatches(
        mood,
        listOfNotNull(item.name, item.plot).joinToString(" ")
    )

    val filteredVod = vodPool.filter(::vodMatches).ifEmpty { vodPool }.shuffled()
    val filteredSeries = seriesPool.filter(::seriesMatches).ifEmpty { seriesPool }.shuffled()

    val result = mutableListOf<MomentCandidate>()
    fun addSeries(count: Int, reason: String) {
        filteredSeries.filterNot { item -> result.any { it.series?.seriesId == item.seriesId } }
            .take(count)
            .forEach { result += MomentCandidate(series = it, reason = reason) }
    }
    fun addVod(count: Int, reason: String) {
        filteredVod.filterNot { item -> result.any { it.vod?.streamId == item.streamId } }
            .take(count)
            .forEach { result += MomentCandidate(vod = it, reason = reason) }
    }

    when (duration) {
        "20 min" -> {
            addSeries(2, "Pensé pour un moment court : une série d’abord, sans te lancer dans un long film.")
            addVod(1, "Alternative Pulse si tu préfères finalement un film.")
        }
        "45 min" -> {
            addSeries(2, "Format idéal pour une session d’environ un épisode.")
            addVod(1, "Option film si tu veux prolonger le moment.")
        }
        "1 h 30" -> {
            addVod(2, "Pulse privilégie ici un film pour remplir ton créneau.")
            addSeries(1, "Alternative série si tu veux garder plus de liberté.")
        }
        "Soirée" -> {
            addVod(2, "Sélection centrale pour une vraie soirée TV.")
            addSeries(1, "Une série en option pour commencer ou finir plus léger.")
        }
        else -> {
            if ((0..1).random() == 0) {
                addVod(2, "Choix surprise basé sur Pulse et les nouveautés.")
                addSeries(1, "Une autre piste pour casser la routine.")
            } else {
                addSeries(2, "Choix surprise basé sur Pulse et tes habitudes.")
                addVod(1, "Un film pour changer complètement de rythme.")
            }
        }
    }
    return result.take(3)
}

private fun momentMoodMatches(mood: String, raw: String): Boolean {
    if (mood == "Tout") return true
    val text = raw
        .lowercase(Locale.ROOT)
        .replace("é", "e")
        .replace("è", "e")
        .replace("ê", "e")
        .replace("à", "a")
        .replace("ô", "o")
    val tokens = when (mood) {
        "Action" -> listOf("action", "guerre", "combat", "aventure", "mission")
        "Détente" -> listOf("comedie", "humour", "famille", "romance", "feel good")
        "Frissons" -> listOf("thriller", "horreur", "crime", "mystere", "suspense")
        "Romance" -> listOf("romance", "romantique", "amour")
        "Famille" -> listOf("famille", "animation", "enfant", "kids")
        else -> emptyList()
    }
    return tokens.any(text::contains)
}

private fun pulseSubtitle(explicit: Set<String>, learned: List<String>): String {
    val explicitLabel = explicit.take(3).joinToString(" • ")
    val learnedLabel = learned.filterNot { it in explicit }.take(2).joinToString(" • ")
    return when {
        explicitLabel.isNotBlank() && learnedLabel.isNotBlank() ->
            "Tes choix : $explicitLabel · Pulse apprend aussi : $learnedLabel"
        explicitLabel.isNotBlank() -> "Inspiré de tes goûts : $explicitLabel"
        learnedLabel.isNotBlank() -> "Pulse l’a appris de tes habitudes : $learnedLabel"
        else -> "Une sélection vivante qui évolue avec tes habitudes."
    }
}

@Composable
private fun InstalledReleaseNotesPopup(
    versionName: String,
    notes: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isTv = context.isTelevisionUi()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, notes) {
        if (isTv) {
            contentFocusRequester.requestFocus()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Nouveautés de TVFlow $versionName",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState)
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRequester(contentFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }

                                    val step = 180
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (scrollState.value < scrollState.maxValue) {
                                                scope.launch {
                                                    scrollState.animateScrollTo(
                                                        (scrollState.value + step)
                                                            .coerceAtMost(scrollState.maxValue)
                                                    )
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }

                                        Key.DirectionUp -> {
                                            if (scrollState.value > 0) {
                                                scope.launch {
                                                    scrollState.animateScrollTo(
                                                        (scrollState.value - step)
                                                            .coerceAtLeast(0)
                                                    )
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }

                                        else -> false
                                    }
                                }
                                .focusable()
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "La mise à jour est installée. Voici ce qui change :",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                notes.forEach { note ->
                    Text(
                        "• $note",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            UpdateActionButton(
                text = "J’ai compris",
                enabled = true,
                primary = true,
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun UpdateActionButton(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        !enabled -> Color.Transparent
        focused -> Color.White
        primary -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.42f)
        focused -> MaterialTheme.colorScheme.background
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        fontWeight = FontWeight.Bold,
        color = foreground
    )
}

@Composable
private fun TonightRail(
    items: List<TonightProgram>,
    onChannel: (LiveChannel) -> Unit
) {
    Column {
        SectionTitle("Les films du prime time")
        Spacer(Modifier.height(6.dp))
        Text(
            "Films prévus entre 20 h 30 et 22 h, selon les informations EPG disponibles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = items,
                key = { it.channel.streamId.toString() + ":" + it.program.startTimestamp }
            ) { item ->
                TonightCard(item, onChannel)
            }
        }
    }
}

@Composable
private fun TonightCard(
    item: TonightProgram,
    onChannel: (LiveChannel) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val now = System.currentTimeMillis() / 1000L
    val liveNow = item.program.isLiveAt(now)
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val start = formatter.format(Date(item.program.startTimestamp * 1000L))

    Column(
        modifier = Modifier
            .width(300.dp)
            .height(142.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            )
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onChannel(item.channel) }
            .focusable(interactionSource = interaction)
            .padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (liveNow) "● EN COURS" else start,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (liveNow) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            if (item.channel.isFavorite) {
                Text("★", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            item.program.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            item.channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
    }
}

@Composable
private fun RecentVodRail(
    items: List<VodItem>,
    onItem: (VodItem) -> Unit,
    title: String = "Sorties récentes • Films",
    subtitle: String = "Classées par vraie date de sortie. Xtream d’abord, TMDb complète les métadonnées manquantes."
) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "vod-" + it.streamId }) { item ->
                RecentMediaCard(
                    title = item.name,
                    imageUrl = item.posterUrl,
                    year = item.releaseYear,
                    month = item.releaseMonth,
                    rating = item.rating,
                    onClick = { onItem(item) }
                )
            }
        }
    }
}

@Composable
private fun RecentSeriesRail(
    items: List<SeriesItem>,
    onItem: (SeriesItem) -> Unit,
    title: String = "Suggestions • Séries récentes"
) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { "series-" + it.seriesId }) { item ->
                RecentMediaCard(
                    title = item.name,
                    imageUrl = item.coverUrl,
                    year = item.releaseYear,
                    month = item.releaseMonth,
                    rating = item.rating,
                    onClick = { onItem(item) }
                )
            }
        }
    }
}

@Composable
private fun RecentMediaCard(
    title: String,
    imageUrl: String?,
    year: Int?,
    month: Int?,
    rating: String?,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(210.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val date = releaseLabel(year, month)
            if (date != null) {
                Text(
                    date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!rating.isNullOrBlank()) {
                Text(
                    "★ " + rating,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                )
            }
        }
    }
}

private fun releaseLabel(year: Int?, month: Int?): String? {
    if (year == null) return null
    val monthName = when (month) {
        1 -> "Janvier"
        2 -> "Février"
        3 -> "Mars"
        4 -> "Avril"
        5 -> "Mai"
        6 -> "Juin"
        7 -> "Juillet"
        8 -> "Août"
        9 -> "Septembre"
        10 -> "Octobre"
        11 -> "Novembre"
        12 -> "Décembre"
        else -> null
    }
    return if (monthName != null) "$monthName $year" else year.toString()
}

@Composable
private fun ChannelRail(
    title: String,
    channels: List<LiveChannel>,
    onChannel: (LiveChannel) -> Unit
) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(channels, key = { it.streamId }) { channel ->
                ChannelCard(channel, onChannel)
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: LiveChannel, onChannel: (LiveChannel) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        label = "channelBorder"
    )
    Column(
        modifier = Modifier
            .width(230.dp)
            .height(94.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(if (focused) 3.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null) { onChannel(channel) }
            .focusable(interactionSource = interaction)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (channel.isFavorite) "★  EN DIRECT" else "EN DIRECT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            channel.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ContinueRail(title: String, items: List<PlaybackPosition>, onResume: (PlaybackPosition) -> Unit) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.mediaKey }) { item ->
                ContinueCard(item, onResume)
            }
        }
    }
}

@Composable
private fun ContinueCard(item: PlaybackPosition, onResume: (PlaybackPosition) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(270.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onResume(item) }
            .focusable(interactionSource = interaction)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${(item.progress * 100).toInt()} % regardé",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(5.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
