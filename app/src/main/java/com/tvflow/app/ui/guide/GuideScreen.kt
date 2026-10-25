@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tvflow.app.ui.guide

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import com.tvflow.app.ui.theme.rainbowBorder
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import com.tvflow.app.domain.EpgProgram
import com.tvflow.app.domain.LiveCategory
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.ui.common.ParentalPinDialog
import com.tvflow.app.ui.common.PlayerLifecycleEffect
import com.tvflow.app.ui.common.iptvMediaSourceFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val RailWidth = 58.dp
private val CategoryWidth = 250.dp
private val ChannelWidthCompact = 178.dp
private val ChannelWidthWide = 270.dp
private const val WindowSeconds = 2 * 60 * 60L

private val Panel = Color(0xFF0B1B2A)
private val PanelSoft = Color(0xFF102C43)
private val PanelSelected = Color(0xFF1A527A)
private val TextSoft = Color(0xFFD7E5EE)

@Composable
fun GuideScreen(
    onPlay: (LiveChannel, String) -> Unit,
    onDashboard: () -> Unit,
    onSearch: () -> Unit,
    onVod: () -> Unit,
    onSeries: () -> Unit,
    onSettings: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var focusedStreamId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(resumed) {
        if (resumed) focusedStreamId?.let(viewModel::focusChannel) else viewModel.stopPreview()
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) viewModel.stopPreview()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.stopPreview() }
    }

    state.pinRequiredCategory?.let { category ->
        ParentalPinDialog(
            categoryName = category.name,
            error = state.pinError,
            onConfirm = viewModel::confirmParentalPin,
            onDismiss = viewModel::dismissParentalPin
        )
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF06111B))
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_INFO -> {
                        viewModel.jumpToNow()
                        true
                    }
                    else -> false
                }
            }
    ) {
        IconRail(
            onDashboard = onDashboard,
            onSearch = onSearch,
            onVod = onVod,
            onSeries = onSeries,
            onSettings = onSettings
        )
        CategoryPanel(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            onSelect = viewModel::selectCategory
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val compactGuide = maxHeight < 500.dp
            Column(Modifier.fillMaxSize()) {
                ProgramHero(
                    previewChannel = state.previewChannel,
                    previewStreamUrl = state.previewStreamUrl.takeIf { resumed },
                    channel = state.selectedChannel,
                    program = state.selectedProgram,
                    loading = state.loading,
                    compact = compactGuide
                )
            state.categories
                .firstOrNull { it.id == state.selectedCategoryId }
                ?.takeIf { it.subFilters.isNotEmpty() }
                ?.let { category ->
                    GuideSubFilterBar(
                        category = category,
                        selectedId = state.selectedSubFilterId,
                        onSelect = viewModel::selectSubFilter,
                        compact = compactGuide
                    )
                }
                GuideToolbar(
                    sort = state.sort,
                    tnt = state.selectedCategoryId == GUIDE_TNT,
                    epgOnly = state.epgOnly,
                    onSort = viewModel::cycleSort,
                    onEpgOnly = viewModel::toggleEpgOnly,
                    compact = compactGuide
                )
            val channelNumbers = remember(state.channels, state.selectedCategoryId) {
                var fallbackNumber = 26
                buildMap {
                    state.channels.forEach { channel ->
                        if (!channel.isSectionMarker) {
                            put(channel.streamId, if (state.selectedCategoryId == GUIDE_TNT) {
                                com.tvflow.app.data.repository.frenchTntNumber(
                                    com.tvflow.app.data.repository.canonicalFrenchChannelName(channel.name)
                                ) ?: fallbackNumber++
                            } else channel.logicalNumber ?: fallbackNumber++)
                        }
                    }
                }
            }
            val guideListState = rememberLazyListState()
            LaunchedEffect(guideListState, state.channels) {
                snapshotFlow {
                    val visible = guideListState.layoutInfo.visibleItemsInfo
                    safeVisibleEpgIds(
                        channels = state.channels,
                        firstVisibleIndex = visible.firstOrNull()?.index,
                        lastVisibleIndex = visible.lastOrNull()?.index
                    )
                }
                    .distinctUntilChanged()
                    .collectLatest(viewModel::prefetchVisibleEpg)
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val channelWidth = when {
                    maxWidth < 560.dp -> ChannelWidthCompact
                    maxWidth < 760.dp -> 220.dp
                    else -> ChannelWidthWide
                }
                val timelineWidth = (maxWidth - channelWidth - 12.dp).coerceAtLeast(220.dp)
                Column(Modifier.fillMaxSize()) {
                    TimelineHeader(
                        windowStart = state.windowStart,
                        channelWidth = channelWidth,
                        timelineWidth = timelineWidth,
                        onPrevious = viewModel::previousWindow,
                        onNow = viewModel::jumpToNow,
                        onNext = viewModel::nextWindow,
                        compact = compactGuide
                    )
                    LazyColumn(
                        state = guideListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(end = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(state.channels, key = { _, item -> item.streamId }) { _, channel ->
                            if (channel.isSectionMarker) {
                                SectionMarker(channel.sectionTitle.orEmpty())
                            } else GuideRow(
                                channelNumber = channelNumbers[channel.streamId] ?: 0,
                                channel = channel,
                                programs = state.timelinePrograms[channel.streamId].orEmpty(),
                                windowStart = state.windowStart,
                                windowEnd = state.windowEnd,
                                channelWidth = channelWidth,
                                timelineWidth = timelineWidth,
                                selected = channel.streamId == state.selectedChannel?.streamId,
                                previewing = channel.streamId == state.previewChannel?.streamId,
                                onSelectChannel = { focusedStreamId = channel.streamId; if (resumed) viewModel.focusChannel(channel.streamId) },
                                onLoseFocus = { if (focusedStreamId == channel.streamId) focusedStreamId = null; viewModel.blurChannel(channel.streamId) },
                                onSelectProgram = { start -> viewModel.selectProgram(channel.streamId, start) },
                                onPreview = { viewModel.previewChannel(channel.streamId) },
                                onPlay = { onPlay(channel, viewModel.capturePlayback()) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun IconRail(
    onDashboard: () -> Unit,
    onSearch: () -> Unit,
    onVod: () -> Unit,
    onSeries: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "tv",
                color = Color(0xFFFFD43B),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 18.dp)
            )
            RailGlyph("⌕", onClick = onSearch)
            RailGlyph("▣", active = true)
            RailGlyph("⌂", onClick = onDashboard)
            RailGlyph("▶", onClick = onVod)
            RailGlyph("S", onClick = onSeries)
        }
        RailGlyph("⚙", onClick = onSettings)
    }
}

@Composable
private fun RailGlyph(
    text: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(width = RailWidth, height = 54.dp)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.24f)
                    active -> Color.White.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (onClick != null) Modifier
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                    .focusable(interactionSource = interaction)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun CategoryPanel(
    categories: List<LiveCategory>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(CategoryWidth)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D7BCB), Color(0xFF0A4E82), Color(0xFF082A45))
                )
            )
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        FocusCategoryItem(
            text = "Favoris",
            selected = selectedId == GUIDE_FAVORITES,
            onClick = { onSelect(GUIDE_FAVORITES) }
        )
        FocusCategoryItem(
            text = "📺 TNT",
            selected = selectedId == GUIDE_TNT,
            onClick = { onSelect(GUIDE_TNT) }
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(categories, key = { it.id }) { category ->
                FocusCategoryItem(
                    text = category.name,
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) }
                )
            }
        }
    }
}

@Composable
private fun FocusCategoryItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            selected -> Color.White.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        label = "categoryFocus"
    )
    Text(
        text = text,
        color = if (focused) Color(0xFF1A405C) else Color.White,
        fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .rainbowBorder(focused || selected, 7.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun ProgramHero(
    previewChannel: LiveChannel?,
    previewStreamUrl: String?,
    channel: LiveChannel?,
    program: EpgProgram?,
    loading: Boolean,
    compact: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 96.dp else 205.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        Color(0xFF0B4E79),
                        Color(0xFF092538)
                    )
                )
            )
            .padding(if (compact) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LivePreview(
            streamUrl = previewStreamUrl,
            channel = previewChannel,
            modifier = Modifier
                .width(if (compact) 176.dp else 330.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF08131D))
        )

        Spacer(Modifier.width(if (compact) 10.dp else 18.dp))

        Column(Modifier.weight(1f)) {
            if (previewChannel != null) {
                Text(
                    text = "APERÇU  •  ${previewChannel.name}",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!compact) Spacer(Modifier.height(4.dp))
            }
            Text(
                text = program?.title ?: channel?.name ?: "Guide TV",
                color = Color.White,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) Spacer(Modifier.height(5.dp))
            if (channel != null) {
                Text(
                    "OK : aperçu  •  OK encore : plein écran",
                    color = Color.White.copy(alpha = 0.68f),
                    style = MaterialTheme.typography.labelSmall
                )
                if (!compact) Spacer(Modifier.height(6.dp))
            }
            if (program != null) {
                val durationMin = ((program.stopTimestamp - program.startTimestamp) / 60).coerceAtLeast(1)
                Text(
                    "${time(program.startTimestamp)} – ${time(program.stopTimestamp)}   •   $durationMin min",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!compact) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = program.description ?: "Aucune description disponible pour ce programme.",
                        color = Color.White.copy(alpha = 0.90f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    if (loading) "Chargement du guide…" else "Sélectionne une chaîne pour afficher le programme.",
                    color = Color.White.copy(alpha = 0.84f)
                )
            }
        }
    }
}


@Composable
private fun LivePreview(
    streamUrl: String?,
    channel: LiveChannel?,
    modifier: Modifier = Modifier
) {
    if (streamUrl.isNullOrBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Aperçu après 2 secondes de focus", color = TextSoft)
        }
        return
    }
    val context = LocalContext.current
    var playbackError by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }

    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1_500,
                6_000,
                400,
                900
            )
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(iptvMediaSourceFactory(context))
            .build()
            .apply {
                playWhenReady = true
                volume = 1f
            }
    }

    PlayerLifecycleEffect(player)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                android.util.Log.i("TVFlowPlayback", "preview first-frame")
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                ready = playbackState == Player.STATE_READY
                if (ready) playbackError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                ready = false
                playbackError = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(streamUrl) {
        ready = false
        playbackError = null
        player.stop()
        player.clearMediaItems()
        if (!streamUrl.isNullOrBlank()) {
            player.setMediaItem(MediaItem.fromUri(streamUrl))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!streamUrl.isNullOrBlank()) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!ready) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF08131D).copy(alpha = if (streamUrl == null) 1f else 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!channel?.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            modifier = Modifier.size(96.dp)
                        )
                    } else {
                        Text(
                            "TV",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            streamUrl.isNullOrBlank() -> "Sélectionne une chaîne puis appuie sur OK"
                            playbackError != null -> "Aperçu indisponible"
                            else -> "Ouverture du direct…"
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    windowStart: Long,
    channelWidth: Dp,
    timelineWidth: Dp,
    onPrevious: () -> Unit,
    onNow: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 36.dp else 42.dp)
            .background(Panel)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(channelWidth),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SimpleDateFormat(
                    if (channelWidth <= ChannelWidthCompact) "HH:mm" else "EEE d MMM, HH:mm",
                    Locale.getDefault()
                ).format(Date()),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            HeaderAction("‹", onPrevious)
            HeaderAction("●", onNow)
            HeaderAction("›", onNext)
        }
        repeat(4) { slot ->
            val start = windowStart + slot * 30 * 60L
            Text(
                text = time(start),
                modifier = Modifier.width(timelineWidth / 4f).padding(horizontal = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GuideSubFilterBar(
    category: LiveCategory,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    compact: Boolean
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A1E2D))
            .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SubFilterChip("Tous", selectedId == null, compact) { onSelect(null) }
        }
        items(category.subFilters, key = { it.id }) { filter ->
            SubFilterChip(filter.name, selectedId == filter.id, compact) { onSelect(filter.id) }
        }
    }
}

@Composable
private fun SubFilterChip(text: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        focused -> Color.White
        selected -> Color(0xFF1E88E5)
        else -> PanelSoft
    }
    Text(
        text = text,
        color = if (focused) Color(0xFF10283A) else Color.White,
        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 5.dp else 8.dp)
    )
}

@Composable
private fun GuideToolbar(sort: GuideSort, tnt: Boolean, epgOnly: Boolean, onSort: () -> Unit, onEpgOnly: () -> Unit, compact: Boolean) {
    val horizontalPadding = if (compact) 11.dp else 14.dp
    val verticalPadding = if (compact) 5.dp else 8.dp
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF081722)).padding(horizontal = 10.dp, vertical = if (compact) 3.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (tnt) "ORDRE : TNT" else "TRI : " + when(sort) { GuideSort.NAME -> "A-Z"; GuideSort.FAVORITES -> "FAVORIS"; GuideSort.EPG -> "EPG" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PanelSoft).clickable(enabled = !tnt, onClick = onSort)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
        Text(
            if (epgOnly) "● AVEC EPG" else "○ AVEC EPG",
            color = if (epgOnly) Color(0xFFFFD54F) else TextSoft,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PanelSoft).clickable(onClick = onEpgOnly)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}

@Composable
private fun SectionMarker(sectionTitle: String) {
    val title = sectionTitle.ifBlank { "Section" }
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF102C42)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(26.dp).background(Color(0xFFFFD54F), RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GuideRow(
    channelNumber: Int,
    channel: LiveChannel,
    programs: List<EpgProgram>,
    windowStart: Long,
    windowEnd: Long,
    channelWidth: Dp,
    timelineWidth: Dp,
    selected: Boolean,
    previewing: Boolean,
    onSelectChannel: () -> Unit,
    onLoseFocus: () -> Unit,
    onSelectProgram: (Long) -> Unit,
    onPreview: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .onFocusChanged { if (it.hasFocus) onSelectChannel() else onLoseFocus() }
            .focusGroup()
            .background(if (selected) PanelSelected else Panel)
            .border(
                width = if (previewing) 1.dp else 0.dp,
                color = if (previewing) Color(0xFFFFD54F) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelCell(
            channelNumber = channelNumber,
            channel = channel,
            channelWidth = channelWidth,
            selected = selected,
            previewing = previewing,
            onSelect = onSelectChannel,
            onPreview = onPreview,
            onPlay = onPlay
        )
        TimelineCells(
            channel = channel,
            programs = programs,
            windowStart = windowStart,
            windowEnd = windowEnd,
            timelineWidth = timelineWidth,
            onSelectProgram = onSelectProgram,
            previewing = previewing,
            onPreview = onPreview,
            onPlay = onPlay
        )
    }
}

@Composable
private fun ChannelCell(
    channelNumber: Int,
    channel: LiveChannel,
    channelWidth: Dp,
    selected: Boolean,
    previewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .width(channelWidth)
            .fillMaxHeight()
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(interactionSource = interaction, indication = null) {
                if (previewing) onPlay() else onPreview()
            }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = channelNumber.toString(),
            color = TextSoft,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(38.dp)
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF2E6A96)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Text("TV", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            channel.name,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TimelineCells(
    channel: LiveChannel,
    programs: List<EpgProgram>,
    windowStart: Long,
    windowEnd: Long,
    timelineWidth: Dp,
    onSelectProgram: (Long) -> Unit,
    previewing: Boolean,
    onPreview: () -> Unit,
    onPlay: () -> Unit
) {
    Box(Modifier.width(timelineWidth).fillMaxHeight()) {
        val visible = programs
            .filter { it.stopTimestamp > windowStart && it.startTimestamp < windowEnd }
            .sortedBy { it.startTimestamp }

        if (visible.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PanelSoft)
                    .clickable(onClick = { if (previewing) onPlay() else onPreview() })
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Pas d'information",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                var cursor = windowStart
                visible.forEach { program ->
                    val clippedStart = max(program.startTimestamp, windowStart)
                    if (clippedStart > cursor) Gap(widthForSeconds(clippedStart - cursor, timelineWidth))
                    val effectiveStart = max(clippedStart, cursor)
                    val clippedEnd = minOf(program.stopTimestamp, windowEnd)
                    if (clippedEnd > effectiveStart) {
                        ProgramBlock(
                            program = program,
                            width = widthForSeconds(clippedEnd - effectiveStart, timelineWidth),
                            onFocus = { onSelectProgram(program.startTimestamp) },
                            onClick = { if (previewing) onPlay() else onPreview() }
                        )
                        cursor = clippedEnd
                    }
                }
                if (cursor < windowEnd) Gap(widthForSeconds(windowEnd - cursor, timelineWidth))
            }
        }

        val now = System.currentTimeMillis() / 1000L
        if (now in windowStart..windowEnd) {
            val x = widthForSeconds(now - windowStart, timelineWidth)
            Box(
                Modifier
                    .offset(x = x)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFFFD54F))
            )
        }
    }
}

@Composable
private fun ProgramBlock(
    program: EpgProgram,
    width: Dp,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val now = System.currentTimeMillis() / 1000L
    val live = program.startTimestamp <= now && now < program.stopTimestamp
    val background by animateColorAsState(
        targetValue = when {
            focused -> Color(0xFFDCEAF4)
            live -> Color(0xFF245E86)
            else -> PanelSoft
        },
        label = "programFocus"
    )

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            program.title,
            color = if (focused) Color(0xFF17344A) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (focused || live) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun Gap(width: Dp) {
    Spacer(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(PanelSoft.copy(alpha = 0.65f))
    )
}

private fun widthForSeconds(seconds: Long, timelineWidth: Dp): Dp =
    timelineWidth * (seconds.toFloat() / WindowSeconds.toFloat())
private fun time(seconds: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(seconds * 1000L))


internal fun safeVisibleEpgIds(
    channels: List<LiveChannel>,
    firstVisibleIndex: Int?,
    lastVisibleIndex: Int?,
    before: Int = 3,
    after: Int = 6
): List<Int> {
    if (channels.isEmpty() || firstVisibleIndex == null || lastVisibleIndex == null) return emptyList()
    val first = (firstVisibleIndex - before).coerceIn(0, channels.lastIndex)
    val last = (lastVisibleIndex + after).coerceIn(0, channels.lastIndex)
    if (first > last) return emptyList()

    return (first..last)
        .mapNotNull(channels::getOrNull)
        .filterNot { it.isSectionMarker }
        .map { it.streamId }
        .distinct()
}
