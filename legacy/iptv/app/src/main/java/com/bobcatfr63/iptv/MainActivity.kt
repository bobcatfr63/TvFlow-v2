package com.bobcatfr63.iptv

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import jp.wasabeef.transformers.coil.BlurTransformation
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media as VlcMedia
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.json.JSONArray
import org.json.JSONObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppBackground = Color(0xFF07111F)
private val SurfaceColor = Color(0xFF102033)
private val SurfaceRaised = Color(0xFF172A40)
private val Accent = Color(0xFF38BDF8)
private val AccentStrong = Color(0xFF0EA5E9)
private val PrimaryText = Color(0xFFF8FAFC)
private val SecondaryText = Color(0xFFD7E2EE)
private val OutlineColor = Color(0xFF6B829A)
private val OnAccent = Color(0xFF00131D)

private val BobcatColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Color(0xFF22D3EE),
    onSecondary = OnAccent,
    background = AppBackground,
    onBackground = PrimaryText,
    surface = SurfaceColor,
    onSurface = PrimaryText,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = SecondaryText,
    outline = OutlineColor,
    error = Color(0xFFFF7474),
    onError = Color.Black
)

enum class HomeTab(val label: String) {
    HOME("Accueil"), LIVE("TV"), GUIDE("Guide TV"), MOVIES("Films"), SERIES("Séries"), NEW("Nouveautés"), FAVORITES("Favoris"), SEARCH("⌕ Recherche")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        requestedOrientation = if (isTelevision()) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setContent {
            MaterialTheme(colorScheme = BobcatColors) {
                val vm: IptvViewModel = viewModel()
                IptvApp(
                    vm = vm,
                    onSwitchProfile = {
                        startActivity(Intent(this@MainActivity, LauncherActivity::class.java))
                        finish()
                    },
                    onReconfigure = {
                        vm.logout()
                        startActivity(Intent(this@MainActivity, LauncherActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val client = XtreamClient(application)
    private val credentialStore = CredentialStore(application)
    private val profileStore = ProfileStore(application)
    val activeProfile = profileStore.active()
    private val cloud = BobcatCloud(application)
    private val prefs = application.getSharedPreferences("iptv_state", 0)
    private val playbackStore = PlaybackStore(application, activeProfile.id)
    private val manualCategoryOnboardingPrefs = application.getSharedPreferences("manual_category_onboarding_v2", 0)

    var credentials by mutableStateOf<Credentials?>(credentialStore.load())
        private set
    var authenticated by mutableStateOf(false)
        private set
    var loading by mutableStateOf(credentials != null)
        private set
    var sectionLoading by mutableStateOf<HomeTab?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var categorySelectionData by mutableStateOf<ServerCategorySelection?>(null)
        private set
    var categorySelectionVisible by mutableStateOf(false)
        private set
    var categorySelectionConfigured by mutableStateOf(false)
        private set
    var selectedLiveCategoryIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedMovieCategoryIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedSeriesCategoryIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var liveCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var liveStreams by mutableStateOf<List<LiveStream>>(emptyList())
        private set
    var vodCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var vodStreams by mutableStateOf<List<VodStream>>(emptyList())
        private set
    var movieSearchResults by mutableStateOf<List<VodStream>>(emptyList())
        private set
    var movieSearchLoading by mutableStateOf(false)
        private set
    private var movieSearchJob: Job? = null
    var homeSearchMovies by mutableStateOf<List<VodStream>>(emptyList())
        private set
    var homeSearchSeries by mutableStateOf<List<SeriesItem>>(emptyList())
        private set
    var homeSearchLoading by mutableStateOf(false)
        private set
    private var homeSearchJob: Job? = null
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var series by mutableStateOf<List<SeriesItem>>(emptyList())
        private set
    var newMovies by mutableStateOf<List<VodStream>>(emptyList())
        private set
    var randomMoviePool by mutableStateOf<List<VodStream>>(emptyList())
        private set
    var newSeries by mutableStateOf<List<SeriesItem>>(emptyList())
        private set
    var newContentLoading by mutableStateOf(false)
        private set
    private var newContentLoaded = false

    private var movieCategoryLoaded: String? = null
    private var seriesCategoryLoaded: String? = null

    var selectedPlayable by mutableStateOf<Playable?>(null)
    var selectedSeries by mutableStateOf<SeriesItem?>(null)
    var selectedEpgStream by mutableStateOf<LiveStream?>(null)
    var seriesInfo by mutableStateOf<SeriesInfoResponse?>(null)
        private set
    var seriesLoading by mutableStateOf(false)
        private set

    val epgCache = mutableStateMapOf<Int, List<EpgItem>>()
    private val epgLoading = mutableStateMapOf<Int, Boolean>()
    private val epgSemaphore = kotlinx.coroutines.sync.Semaphore(6)

    var history by mutableStateOf(playbackStore.records())
        private set

    var favorites by mutableStateOf(
        prefs.getStringSet("favorites_${activeProfile.id}", emptySet())?.toSet() ?: emptySet()
    )
        private set
    var cloudConnected by mutableStateOf(cloud.session() != null)
        private set
    var cloudDevices by mutableStateOf<List<CloudDevice>>(emptyList())
        private set
    var localCloudDeviceId by mutableStateOf(cloud.currentDeviceId())
        private set
    var cloudMessage by mutableStateOf<String?>(null)
        private set
    private val cloudPlaybackPositions = mutableStateMapOf<String, Pair<Long, Long>>()
    var remotePlaybackAction by mutableStateOf<Pair<Long, String>?>(null)
        private set

    init {
        playbackStore.clearFinished()
        history = playbackStore.records()
        credentials?.let { login(it, save = false) }
        if (cloudConnected) startCloud()
    }

    fun cloudLogin(email: String, password: String, create: Boolean) {
        viewModelScope.launch {
            cloudMessage = "Connexion au cloud…"
            runCatching { if (create) cloud.signUp(email, password) else cloud.signIn(email, password) }
                .onSuccess { cloudConnected = true; cloudMessage = "Compte Bobcat connecté"; startCloud() }
                .onFailure { cloudMessage = it.message ?: "Connexion cloud impossible" }
        }
    }

    fun cloudLogout() { cloud.signOut(); cloudConnected = false; cloudDevices = emptyList(); cloudMessage = "Cloud déconnecté" }
    fun clearCloudMessage() { cloudMessage = null }

    private fun startCloud() {
        viewModelScope.launch {
            runCatching {
                val device = cloud.registerDevice()
                localCloudDeviceId = device.id
                syncCloudNow()
                while (cloudConnected) {
                    runCatching {
                        cloudDevices = cloud.devices()
                        cloud.pendingCommands(device.id).forEach { command ->
                            when (command.command) {
                                "open" -> command.playable?.let { selectedPlayable = rebuildRemotePlayable(it) }
                                "stop" -> selectedPlayable = null
                                "pause", "resume", "play" -> remotePlaybackAction = command.id to command.command
                            }
                            cloud.consumeCommand(command.id)
                        }
                    }.onFailure { failure ->
                        if (failure.message?.contains("401") == true) runCatching { cloud.refreshSession() }
                        else cloudMessage = failure.message ?: "Synchronisation cloud interrompue"
                    }
                    delay(5_000)
                }
            }.onFailure { cloudMessage = it.message ?: "Synchronisation cloud interrompue" }
        }
    }

    fun syncCloudNow() {
        if (!cloudConnected) return
        viewModelScope.launch {
            runCatching {
                val remoteFavorites = cloud.pullState(activeProfile.id, "favorites")?.optJSONArray("items")
                if (remoteFavorites != null) {
                    val merged = favorites.toMutableSet()
                    for (i in 0 until remoteFavorites.length()) merged += remoteFavorites.getString(i)
                    favorites = merged
                    prefs.edit().putStringSet("favorites_${activeProfile.id}", favorites).apply()
                }
                cloud.pushState(activeProfile.id, "favorites", JSONObject().put("items", JSONArray(favorites.toList())))
                val remotePlayback = cloud.pullState(activeProfile.id, "playback")?.optJSONArray("items")
                if (remotePlayback != null) for (i in 0 until remotePlayback.length()) remotePlayback.getJSONObject(i).let {
                    cloudPlaybackPositions[it.getString("key")] = it.optLong("position") to it.optLong("duration")
                }
                val playback = JSONArray()
                history.forEach { r -> playback.put(JSONObject().put("key",r.key).put("position",r.positionMs).put("duration",r.durationMs).put("updated",r.updatedAt)) }
                cloud.pushState(activeProfile.id, "playback", JSONObject().put("items", playback))
                cloudMessage = "Synchronisation terminée"
            }.onFailure { cloudMessage = it.message ?: "Synchronisation impossible" }
        }
    }

    fun sendToTv(playable: Playable) {
        val tv = targetTv()
        if (tv == null) { cloudMessage = "Aucune box Bobcat connectée"; return }
        viewModelScope.launch { runCatching { cloud.sendOpen(tv.id, playable) }.onSuccess { cloudMessage = "Lecture envoyée vers ${tv.name}" }.onFailure { cloudMessage = it.message } }
    }

    fun sendRemoteControl(command: String) {
        val tv = targetTv()
        if (tv == null) { cloudMessage = "Aucune box Bobcat connectée"; return }
        viewModelScope.launch { runCatching { cloud.sendControl(tv.id, command) }.onSuccess { cloudMessage = "Commande envoyée" }.onFailure { cloudMessage = it.message } }
    }

    private fun targetTv(): CloudDevice? {
        val preferred = cloud.preferredTvId()
        return cloudDevices.firstOrNull { it.id == preferred && it.platform == "tv" }
            ?: cloudDevices.firstOrNull { it.platform == "tv" && it.id != localCloudDeviceId }
    }

    private fun rebuildRemotePlayable(remote: Playable): Playable {
        val c = credentials ?: return remote
        val id = remote.contentKey.substringAfter(':')
        val url = when (remote.type) {
            ContentType.LIVE -> client.liveUrl(c, id.toIntOrNull() ?: return remote)
            ContentType.MOVIE -> client.movieUrl(c, id.toIntOrNull() ?: return remote, remote.url)
            ContentType.SERIES_EPISODE -> client.episodeUrl(c, id, remote.url)
        }
        return remote.copy(url = url)
    }

    fun openCategorySelection() {
        val c = credentials ?: return
        viewModelScope.launch {
            sectionLoading = HomeTab.LIVE
            try {
                val selection = client.loadCategorySelection(c)
                categorySelectionData = selection
                selectedLiveCategoryIds = selection.selectedLiveIds
                selectedMovieCategoryIds = selection.selectedMovieIds
                selectedSeriesCategoryIds = selection.selectedSeriesIds
                categorySelectionConfigured = selection.configured
                categorySelectionVisible = true
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de charger les catégories du serveur"
            } finally {
                sectionLoading = null
            }
        }
    }

    fun closeCategorySelection() {
        if (categorySelectionConfigured) categorySelectionVisible = false
    }

    fun toggleManualCategory(type: HomeTab, id: String) {
        when (type) {
            HomeTab.LIVE -> selectedLiveCategoryIds = if (id in selectedLiveCategoryIds) selectedLiveCategoryIds - id else selectedLiveCategoryIds + id
            HomeTab.MOVIES -> selectedMovieCategoryIds = if (id in selectedMovieCategoryIds) selectedMovieCategoryIds - id else selectedMovieCategoryIds + id
            HomeTab.SERIES -> selectedSeriesCategoryIds = if (id in selectedSeriesCategoryIds) selectedSeriesCategoryIds - id else selectedSeriesCategoryIds + id
            else -> Unit
        }
    }

    fun setManualCategorySection(type: HomeTab, ids: Set<String>, checked: Boolean) {
        when (type) {
            HomeTab.LIVE -> selectedLiveCategoryIds = if (checked) selectedLiveCategoryIds + ids else selectedLiveCategoryIds - ids
            HomeTab.MOVIES -> selectedMovieCategoryIds = if (checked) selectedMovieCategoryIds + ids else selectedMovieCategoryIds - ids
            HomeTab.SERIES -> selectedSeriesCategoryIds = if (checked) selectedSeriesCategoryIds + ids else selectedSeriesCategoryIds - ids
            else -> Unit
        }
    }

    fun saveManualCategorySelection() {
        val c = credentials ?: return
        client.saveCategorySelection(c, selectedLiveCategoryIds, selectedMovieCategoryIds, selectedSeriesCategoryIds)
        manualCategoryOnboardingPrefs.edit().putBoolean("done", true).apply()
        categorySelectionConfigured = true
        categorySelectionVisible = false
        viewModelScope.launch {
            sectionLoading = HomeTab.LIVE
            try {
                liveCategories = emptyList()
                liveStreams = emptyList()
                vodCategories = emptyList()
                vodStreams = emptyList()
                seriesCategories = emptyList()
                series = emptyList()
                movieCategoryLoaded = null
                seriesCategoryLoaded = null
                reloadLiveInternal(c)
            } catch (t: Throwable) {
                error = t.message ?: "Impossible d'appliquer la sélection de catégories"
            } finally {
                sectionLoading = null
            }
        }
    }

    fun login(value: Credentials, save: Boolean = true) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val auth = client.authenticate(value)
                if (auth.userInfo?.auth != 1) {
                    throw IllegalStateException(auth.userInfo?.message.ifNullOrBlank("Connexion Xtream refusée"))
                }
                credentials = value
                if (save) credentialStore.save(value)
                val selection = client.loadCategorySelection(value)
                categorySelectionData = selection
                selectedLiveCategoryIds = selection.selectedLiveIds
                selectedMovieCategoryIds = selection.selectedMovieIds
                selectedSeriesCategoryIds = selection.selectedSeriesIds
                categorySelectionConfigured = selection.configured
                val onboardingDone = manualCategoryOnboardingPrefs.getBoolean("done", false)
                if (selection.configured) {
                    reloadLiveInternal(value)
                } else {
                    liveCategories = emptyList()
                    liveStreams = emptyList()
                }
                categorySelectionVisible = !selection.configured || !onboardingDone
                authenticated = true
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de joindre le serveur Xtream"
                authenticated = false
            } finally {
                loading = false
            }
        }
    }

    private suspend fun reloadLiveInternal(c: Credentials) {
        liveCategories = filterCategories(client.liveCategories(c))
        val allowedIds = liveCategories.map { it.id }.toSet()
        liveStreams = client.liveStreams(c).filter { it.categoryId in allowedIds }
        epgCache.clear()
    }

    private fun filterCategories(categories: List<Category>): List<Category> =
        categories.filter { activeProfile.allowsCategory(it.name) }

    fun ensureMovieCategories() {
        val c = credentials ?: return
        if (vodCategories.isNotEmpty() || sectionLoading == HomeTab.MOVIES) return
        viewModelScope.launch {
            sectionLoading = HomeTab.MOVIES
            error = null
            try { vodCategories = filterCategories(client.vodCategories(c)) }
            catch (t: Throwable) { error = t.message ?: "Impossible de charger les catégories de films" }
            finally { sectionLoading = null }
        }
    }

    fun loadMovieCategory(categoryId: String, force: Boolean = false) {
        val c = credentials ?: return
        if (!force && movieCategoryLoaded == categoryId && vodStreams.isNotEmpty()) return
        viewModelScope.launch {
            sectionLoading = HomeTab.MOVIES
            error = null
            try {
                if (vodCategories.isEmpty()) vodCategories = filterCategories(client.vodCategories(c))
                vodStreams = client.vodStreams(c, categoryId)
                movieCategoryLoaded = categoryId
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de charger les films de cette catégorie"
                vodStreams = emptyList()
            } finally { sectionLoading = null }
        }
    }

    fun searchMoviesGlobally(query: String) {
        movieSearchJob?.cancel()
        val q = query.trim()
        if (q.isBlank()) {
            movieSearchResults = emptyList()
            movieSearchLoading = false
            return
        }
        val c = credentials ?: return
        movieSearchJob = viewModelScope.launch {
            delay(300)
            movieSearchLoading = true
            error = null
            try {
                if (vodCategories.isEmpty()) vodCategories = filterCategories(client.vodCategories(c))
                val found = linkedMapOf<Int, VodStream>()
                for (category in vodCategories) {
                    val chunk = runCatching { client.vodStreams(c, category.id) }.getOrDefault(emptyList())
                    chunk.asSequence()
                        .filter { it.name.contains(q, ignoreCase = true) }
                        .forEach { found[it.id] = it }
                    movieSearchResults = found.values.toList()
                }
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de rechercher dans les films"
            } finally {
                movieSearchLoading = false
            }
        }
    }

    fun searchHomeGlobally(query: String) {
        homeSearchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            homeSearchMovies = emptyList()
            homeSearchSeries = emptyList()
            homeSearchLoading = false
            return
        }
        val c = credentials ?: return
        homeSearchJob = viewModelScope.launch {
            delay(300)
            homeSearchLoading = true
            error = null
            try {
                if (vodCategories.isEmpty()) vodCategories = filterCategories(client.vodCategories(c))
                if (seriesCategories.isEmpty()) seriesCategories = filterCategories(client.seriesCategories(c))

                val hiddenMovies = prefs.getStringSet("category_hidden_MOVIES", emptySet())?.toSet().orEmpty()
                val hiddenSeries = prefs.getStringSet("category_hidden_SERIES", emptySet())?.toSet().orEmpty()

                val movieFound = linkedMapOf<Int, VodStream>()
                for (category in vodCategories.filterNot { it.id in hiddenMovies }) {
                    val chunk = runCatching { client.vodStreams(c, category.id) }.getOrDefault(emptyList())
                    chunk.asSequence()
                        .filter { it.name.contains(q, ignoreCase = true) }
                        .forEach { movieFound[it.id] = it }
                    homeSearchMovies = movieFound.values.take(100)
                }

                val seriesFound = linkedMapOf<Int, SeriesItem>()
                for (category in seriesCategories.filterNot { it.id in hiddenSeries }) {
                    val chunk = runCatching { client.series(c, category.id) }.getOrDefault(emptyList())
                    chunk.asSequence()
                        .filter { it.name.contains(q, ignoreCase = true) }
                        .forEach { seriesFound[it.id] = it }
                    homeSearchSeries = seriesFound.values.take(100)
                }
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de rechercher dans le catalogue"
            } finally {
                homeSearchLoading = false
            }
        }
    }

    fun ensureSeriesCategories() {
        val c = credentials ?: return
        if (seriesCategories.isNotEmpty() || sectionLoading == HomeTab.SERIES) return
        viewModelScope.launch {
            sectionLoading = HomeTab.SERIES
            error = null
            try { seriesCategories = filterCategories(client.seriesCategories(c)) }
            catch (t: Throwable) { error = t.message ?: "Impossible de charger les catégories de séries" }
            finally { sectionLoading = null }
        }
    }

    fun loadSeriesCategory(categoryId: String, force: Boolean = false) {
        val c = credentials ?: return
        if (!force && seriesCategoryLoaded == categoryId && series.isNotEmpty()) return
        viewModelScope.launch {
            sectionLoading = HomeTab.SERIES
            error = null
            try {
                if (seriesCategories.isEmpty()) seriesCategories = filterCategories(client.seriesCategories(c))
                series = client.series(c, categoryId)
                seriesCategoryLoaded = categoryId
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de charger les séries de cette catégorie"
                series = emptyList()
            } finally { sectionLoading = null }
        }
    }

    fun ensureNewContent(force: Boolean = false) {
        val c = credentials ?: return
        if ((!force && newContentLoaded) || newContentLoading) return
        viewModelScope.launch {
            newContentLoading = true
            error = null
            try {
                if (vodCategories.isEmpty()) vodCategories = filterCategories(client.vodCategories(c))
                if (seriesCategories.isEmpty()) seriesCategories = filterCategories(client.seriesCategories(c))

                val hiddenMovies = emptySet<String>()
                val favoriteMovies = prefs.getStringSet("category_favorite_MOVIES", emptySet())?.toSet().orEmpty()
                val hiddenSeries = emptySet<String>()
                val favoriteSeries = prefs.getStringSet("category_favorite_SERIES", emptySet())?.toSet().orEmpty()

                val movieCategories = vodCategories
                    .filterNot { it.id in hiddenMovies }
                    .sortedWith(compareByDescending<Category> {
                        it.id in favoriteMovies || it.name.contains("récent", true) || it.name.contains("ajout", true) || it.name.contains("nouveau", true)
                    }.thenBy { it.name.lowercase() })
                    .take(10)
                val showCategories = seriesCategories
                    .filterNot { it.id in hiddenSeries }
                    .sortedWith(compareByDescending<Category> {
                        it.id in favoriteSeries || it.name.contains("récent", true) || it.name.contains("ajout", true) || it.name.contains("nouveau", true)
                    }.thenBy { it.name.lowercase() })
                    .take(8)

                val (moviePool, seriesPool) = coroutineScope {
                    val movies = async {
                        movieCategories.map { category ->
                            async {
                                runCatching { client.vodStreams(c, category.id) }.getOrDefault(emptyList())
                                    .sortedByDescending { movieReleaseTimestamp(it) }.take(60)
                            }
                        }.awaitAll().flatten()
                    }
                    val shows = async {
                        showCategories.map { category ->
                            async {
                                runCatching { client.series(c, category.id) }.getOrDefault(emptyList())
                                    .sortedByDescending { seriesReleaseTimestamp(it) }.take(60)
                            }
                        }.awaitAll().flatten()
                    }
                    movies.await() to shows.await()
                }

                val distinctMovies = moviePool.distinctBy { it.id }
                randomMoviePool = distinctMovies.take(500)
                newMovies = distinctMovies.filter { movieReleaseTimestamp(it) > 0L }.sortedByDescending { movieReleaseTimestamp(it) }.take(60)
                newSeries = seriesPool.distinctBy { it.id }.filter { seriesReleaseTimestamp(it) > 0L }.sortedByDescending { seriesReleaseTimestamp(it) }.take(60)
                newContentLoaded = true
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de charger les nouveautés"
            } finally {
                newContentLoading = false
            }
        }
    }

    fun playRandomMovie() {
        randomMoviePool.randomOrNull()?.let {
            playMovie(it)
            return
        }
        val c = credentials ?: return
        viewModelScope.launch {
            try {
                if (vodCategories.isEmpty()) vodCategories = filterCategories(client.vodCategories(c))
                for (category in vodCategories.shuffled().take(12)) {
                    val candidate = runCatching { client.vodStreams(c, category.id) }
                        .getOrDefault(emptyList())
                        .randomOrNull()
                    if (candidate != null) {
                        playMovie(candidate)
                        return@launch
                    }
                }
            } catch (t: Throwable) {
                error = t.message ?: "Impossible de choisir un film au hasard"
            }
        }
    }

    fun reload(tab: HomeTab, categoryId: String?) {
        val c = credentials ?: return
        when (tab) {
            HomeTab.HOME, HomeTab.LIVE, HomeTab.GUIDE -> viewModelScope.launch {
                sectionLoading = HomeTab.LIVE
                error = null
                try { reloadLiveInternal(c) }
                catch (t: Throwable) { error = t.message ?: "Impossible de recharger la TV" }
                finally { sectionLoading = null }
            }
            HomeTab.MOVIES -> {
                if (categoryId != null) loadMovieCategory(categoryId, true)
                else viewModelScope.launch {
                    sectionLoading = HomeTab.MOVIES
                    try {
                        vodCategories = filterCategories(client.vodCategories(c))
                        vodStreams = emptyList()
                        movieCategoryLoaded = null
                    } catch (t: Throwable) { error = t.message ?: "Impossible de recharger les films" }
                    finally { sectionLoading = null }
                }
            }
            HomeTab.SERIES -> {
                if (categoryId != null) loadSeriesCategory(categoryId, true)
                else viewModelScope.launch {
                    sectionLoading = HomeTab.SERIES
                    try {
                        seriesCategories = filterCategories(client.seriesCategories(c))
                        series = emptyList()
                        seriesCategoryLoaded = null
                    } catch (t: Throwable) { error = t.message ?: "Impossible de recharger les séries" }
                    finally { sectionLoading = null }
                }
            }
            HomeTab.NEW -> ensureNewContent(force = true)
            HomeTab.FAVORITES, HomeTab.SEARCH -> Unit
        }
    }

    fun ensureEpg(streamId: Int) {
        val c = credentials ?: return
        if (streamId in epgCache || epgLoading[streamId] == true) return
        epgLoading[streamId] = true
        viewModelScope.launch {
            var acquired = false
            try {
                epgSemaphore.acquire()
                acquired = true
                val items = runCatching { client.shortEpg(c, streamId) }.getOrDefault(emptyList())
                if (epgCache.size >= 120 && streamId !in epgCache) {
                    epgCache.keys.firstOrNull()?.let { epgCache.remove(it) }
                }
                epgCache[streamId] = items
            } finally {
                if (acquired) epgSemaphore.release()
                epgLoading.remove(streamId)
            }
        }
    }

    fun openEpg(item: LiveStream) {
        selectedEpgStream = item
        ensureEpg(item.id)
    }

    fun playLive(item: LiveStream) {
        val c = credentials ?: return
        val p = Playable(item.name, client.liveUrl(c, item.id), ContentType.LIVE, "live:${item.id}", item.categoryId)
        playbackStore.recordStart(p, item.icon)
        history = playbackStore.records()
        selectedPlayable = p
    }

    fun playMovie(item: VodStream) {
        val c = credentials ?: return
        val p = Playable(
            item.name,
            client.movieUrl(c, item.id, item.extension),
            ContentType.MOVIE,
            "movie:${item.id}",
            item.categoryId,
            item.rating
        )
        playbackStore.recordStart(p, item.icon)
        history = playbackStore.records()
        selectedPlayable = p
    }

    fun moviePreviewUrl(item: VodStream): String? {
        val c = credentials ?: return null
        return client.movieUrl(c, item.id, item.extension)
    }

    fun openSeries(item: SeriesItem) {
        val c = credentials ?: return
        selectedSeries = item
        seriesInfo = null
        seriesLoading = true
        viewModelScope.launch {
            seriesInfo = runCatching { client.seriesInfo(c, item.id) }.getOrElse {
                error = it.message
                null
            }
            seriesLoading = false
        }
    }

    fun playEpisode(episode: Episode) {
        val c = credentials ?: return
        val seriesItem = selectedSeries
        val p = Playable(
            episode.title.ifBlank { "Épisode ${episode.episodeNumber}" },
            client.episodeUrl(c, episode.id, episode.extension),
            ContentType.SERIES_EPISODE,
            "episode:${episode.id}",
            seriesItem?.categoryId.orEmpty(),
            seriesItem?.rating
        )
        playbackStore.recordStart(p, seriesItem?.cover)
        history = playbackStore.records()
        selectedPlayable = p
    }

    fun playFromHistory(record: PlaybackRecord) {
        playbackStore.recordStart(record.toPlayable(), record.image)
        history = playbackStore.records()
        selectedPlayable = record.toPlayable()
    }

    fun resumePosition(playable: Playable): Long {
        val local = playbackStore.resumePosition(playable)
        val remote = cloudPlaybackPositions[playable.contentKey]?.first ?: 0L
        return maxOf(local, remote)
    }

    fun saveProgress(playable: Playable, positionMs: Long, durationMs: Long) {
        playbackStore.saveProgress(playable, positionMs, durationMs)
        history = playbackStore.records()
    }

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit().putStringSet("favorites_${activeProfile.id}", favorites).apply()
        syncCloudNow()
    }

    fun recommendedMovies(): List<VodStream> {
        val watched = history.map { it.key }.toSet()
        return vodStreams
            .filterNot { "movie:${it.id}" in watched }
            .sortedByDescending { ratingValue(it.rating, it.rating5) }
            .take(12)
    }

    fun recommendedSeries(): List<SeriesItem> {
        return series.sortedByDescending { ratingValue(it.rating, it.rating5) }.take(12)
    }

    fun logout() {
        credentialStore.clear()
        credentials = null
        authenticated = false
        selectedPlayable = null
        selectedSeries = null
        selectedEpgStream = null
        liveCategories = emptyList()
        liveStreams = emptyList()
        vodCategories = emptyList()
        vodStreams = emptyList()
        seriesCategories = emptyList()
        series = emptyList()
        newMovies = emptyList()
        newSeries = emptyList()
        randomMoviePool = emptyList()
        newContentLoaded = false
        movieCategoryLoaded = null
        seriesCategoryLoaded = null
        epgCache.clear()
    }

    private fun String?.ifNullOrBlank(fallback: String) = if (this.isNullOrBlank()) fallback else this
}

private fun ratingValue(rating: String?, rating5: Double? = null): Double {
    val raw = rating?.replace(',', '.')?.toDoubleOrNull()
    return when {
        raw != null && raw > 5.0 -> raw
        raw != null -> raw * 2.0
        rating5 != null -> rating5 * 2.0
        else -> 0.0
    }
}

private fun displayRating(rating: String?, rating5: Double? = null): String? {
    val value = ratingValue(rating, rating5)
    return if (value > 0.0) "★ ${String.format(Locale.FRANCE, "%.1f", value)}/10" else null
}

@Composable
fun IptvApp(vm: IptvViewModel, onSwitchProfile: () -> Unit, onReconfigure: () -> Unit) {
    UpdateUi(visible = vm.selectedPlayable == null)
    when {
        vm.loading -> LoadingScreen()
        !vm.authenticated -> ConnectionProblemScreen(vm.error, onReconfigure)
        else -> {
            val playable = vm.selectedPlayable
            if (playable == null) {
                HomeScreen(vm, onSwitchProfile, onReconfigure)
            } else {
                val liveIndex = if (playable.type == ContentType.LIVE) {
                    vm.liveStreams.indexOfFirst { "live:${it.id}" == playable.contentKey }
                } else -1
                PlayerScreen(
                    playable = playable,
                    resumePosition = vm.resumePosition(playable),
                    onProgress = { position, duration -> vm.saveProgress(playable, position, duration) },
                    onBack = { vm.selectedPlayable = null },
                    onSendToTv = if (vm.cloudConnected) ({ vm.sendToTv(playable) }) else null,
                    remoteAction = vm.remotePlaybackAction,
                    onPrevious = if (liveIndex > 0) {{ vm.playLive(vm.liveStreams[liveIndex - 1]) }} else null,
                    onNext = if (liveIndex >= 0 && liveIndex < vm.liveStreams.lastIndex) {{ vm.playLive(vm.liveStreams[liveIndex + 1]) }} else null
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = RoundedCornerShape(26.dp),
                color = Accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("B", color = OnAccent, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                }
            }
            Text("BOBCAT IPTV", color = PrimaryText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Préparation de votre expérience cinéma", color = SecondaryText)
            LinearProgressIndicator(
                modifier = Modifier.width(300.dp),
                color = Accent,
                trackColor = SurfaceRaised
            )
        }
    }
}

private fun Activity.isTelevision(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

@Composable
private fun ConnectionProblemScreen(error: String?, onReconfigure: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Connexion impossible", style = MaterialTheme.typography.headlineMedium)
            Text(error ?: "Les identifiants doivent être vérifiés.", color = SecondaryText)
            Button(onClick = onReconfigure) { Text("Modifier la connexion") }
        }
    }
}

@Composable
fun HomeScreen(vm: IptvViewModel, onSwitchProfile: () -> Unit, onReconfigure: () -> Unit) {
    val compactPhone = LocalConfiguration.current.screenWidthDp < 600
    val context = androidx.compose.ui.platform.LocalContext.current
    var previousCrash by remember { mutableStateOf(CrashReporter.consume(context)) }
    val categoryPreferences = remember { CategoryPreferences(context) }
    var categoryRevision by remember { mutableIntStateOf(0) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showMobileMenu by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(HomeTab.HOME) }
    var query by remember { mutableStateOf("") }
    var liveCategory by remember { mutableStateOf<String?>(null) }
    var movieCategory by remember { mutableStateOf<String?>(null) }
    var seriesCategory by remember { mutableStateOf<String?>(null) }

    val liveListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()

    LaunchedEffect(tab) {
        when (tab) {
            HomeTab.MOVIES -> vm.ensureMovieCategories()
            HomeTab.SERIES -> vm.ensureSeriesCategories()
            HomeTab.NEW -> vm.ensureNewContent()
            else -> Unit
        }
    }

    val category = when (tab) {
        HomeTab.LIVE -> liveCategory
        HomeTab.MOVIES -> movieCategory
        HomeTab.SERIES -> seriesCategory
        else -> null
    }

    val rawCategories = when (tab) {
        HomeTab.LIVE -> vm.liveCategories
        HomeTab.MOVIES -> vm.vodCategories
        HomeTab.SERIES -> vm.seriesCategories
        else -> emptyList()
    }
    val categories = rawCategories

    fun selectCategory(value: String?) {
        when (tab) {
            HomeTab.LIVE -> liveCategory = value
            HomeTab.MOVIES -> {
                movieCategory = value
                if (value != null) vm.loadMovieCategory(value)
            }
            HomeTab.SERIES -> {
                seriesCategory = value
                if (value != null) vm.loadSeriesCategory(value)
            }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(AppBackground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceColor).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Bobcat IPTV", color = PrimaryText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!compactPhone) Text("Votre TV, films et séries", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { tab = HomeTab.SEARCH }) { Text(if (compactPhone) "⌕" else "Recherche", color = Accent) }
            TextButton(onClick = onSwitchProfile) { Text(if (compactPhone) vm.activeProfile.name.take(1) else "Profil : ${vm.activeProfile.name}", color = Accent) }
            if (!compactPhone) {
                TextButton(onClick = { showCloud = true }) { Text("Cloud", color = Accent) }
                TextButton(onClick = vm::openCategorySelection) { Text("Catégories", color = Accent) }
                TextButton(onClick = { vm.reload(tab, category) }) { Text("↻", color = Accent, style = MaterialTheme.typography.titleLarge) }
                TextButton(onClick = onReconfigure) { Text("Déconnexion", color = SecondaryText) }
            } else {
                Box {
                    TextButton(onClick = { showMobileMenu = true }) { Text("⋮", color = PrimaryText, style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = showMobileMenu, onDismissRequest = { showMobileMenu = false }) {
                        DropdownMenuItem(text = { Text("Actualiser") }, onClick = { showMobileMenu = false; vm.reload(tab, category) })
                        DropdownMenuItem(text = { Text("Guide TV") }, onClick = { showMobileMenu = false; tab = HomeTab.GUIDE })
                        DropdownMenuItem(text = { Text("Compte et appareils") }, onClick = { showMobileMenu = false; showCloud = true })
                        DropdownMenuItem(text = { Text("Gérer les catégories") }, onClick = { showMobileMenu = false; vm.openCategorySelection() })
                        DropdownMenuItem(text = { Text("Déconnexion") }, onClick = { showMobileMenu = false; onReconfigure() })
                    }
                }
            }
        }

        if (!compactPhone) LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(HomeTab.entries, key = { it.name }) { current ->
                FilterChip(
                    selected = tab == current,
                    onClick = {
                        tab = current
                        query = ""
                        showCategoryManager = false
                        if (current == HomeTab.MOVIES) vm.searchMoviesGlobally("")
                    },
                    label = { Text(current.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceRaised,
                        labelColor = PrimaryText,
                        selectedContainerColor = AccentStrong,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tab == HomeTab.LIVE) item { CategoryChip(category == null, "Tout") { selectCategory(null) } }
                items(categories, key = { it.id }) { entry ->
                    val label = if (categoryPreferences.isFavorite(tab, entry.id)) "★ ${entry.name}" else entry.name
                    CategoryChip(category == entry.id, label) { selectCategory(entry.id) }
                }
            }
        }

        vm.error?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                HomeTab.HOME -> HomeDashboard(vm)
                HomeTab.LIVE -> LiveList(vm, liveCategory, query, liveListState)
                HomeTab.GUIDE -> EpgGuideScreen(vm)
                HomeTab.MOVIES -> if (movieCategory == null && query.isBlank()) {
                    EmptyHint("Choisis une catégorie de films ou recherche dans tout le catalogue")
                } else MovieGrid(vm, query, movieCategory ?: "global")
                HomeTab.SERIES -> if (seriesCategory == null) {
                    EmptyHint("Choisis une catégorie de séries")
                } else SeriesGrid(vm, query, seriesCategory ?: "series")
                HomeTab.NEW -> NewContentScreen(vm.newMovies, vm.newSeries, vm.newContentLoading, vm::playMovie, vm::openSeries)
                HomeTab.FAVORITES -> FavoritesList(vm, query, favoritesListState)
                HomeTab.SEARCH -> HomeGlobalSearch(vm)
            }

            if (vm.sectionLoading == tab || (tab == HomeTab.HOME && vm.sectionLoading == HomeTab.LIVE)) {
                Box(Modifier.fillMaxSize().background(AppBackground.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(14.dp))
                        Text("Chargement…", color = PrimaryText)
                    }
                }
            }
        }

        if (compactPhone) {
            val phoneTabs = listOf(HomeTab.HOME, HomeTab.LIVE, HomeTab.MOVIES, HomeTab.SERIES, HomeTab.FAVORITES)
            NavigationBar(containerColor = SurfaceColor) {
                phoneTabs.forEach { current ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = { tab = current; query = "" },
                        icon = { Text(when (current) {
                            HomeTab.HOME -> "⌂"; HomeTab.LIVE -> "TV"; HomeTab.MOVIES -> "▶"; HomeTab.SERIES -> "▣"; else -> "★"
                        }) },
                        label = { Text(current.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = OnAccent, selectedTextColor = Accent, indicatorColor = Accent)
                    )
                }
            }
        }
    }

    if (showCategoryManager && (tab == HomeTab.LIVE || tab == HomeTab.MOVIES || tab == HomeTab.SERIES)) {
        CategoryManagerDialog(
            tab = tab,
            categories = rawCategories,
            preferences = categoryPreferences,
            revision = categoryRevision,
            onChanged = {
                categoryRevision++
                if (tab == HomeTab.NEW) vm.ensureNewContent(force = true)
            },
            onDismiss = { showCategoryManager = false }
        )
    }
    previousCrash?.let { report ->
        AlertDialog(
            onDismissRequest = { previousCrash = null },
            confirmButton = { TextButton(onClick = { previousCrash = null }) { Text("Fermer") } },
            title = { Text("Rapport du lecteur") },
            text = {
                Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                    Text("Bobcat IPTV a enregistré les détails des différentes tentatives.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(report, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }
    vm.selectedSeries?.let { selected ->
        SeriesDialog(vm, selected, onDismiss = { vm.selectedSeries = null })
    }
    vm.selectedEpgStream?.let { stream ->
        EpgDialog(stream, vm.epgCache[stream.id].orEmpty(), onDismiss = { vm.selectedEpgStream = null })
    }
    if (vm.categorySelectionVisible) {
        ManualCategorySelectionDialog(vm)
    }
    if (showCloud) CloudPanelDialog(vm) { showCloud = false }
}

@Composable
private fun CloudPanelDialog(vm: IptvViewModel, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compte Bobcat Cloud") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!vm.cloudConnected) {
                    Text("Utilisez le même compte sur le téléphone et la box pour synchroniser et piloter la lecture.")
                    OutlinedTextField(email, { email = it }, label = { Text("Adresse e-mail") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Mot de passe") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.cloudLogin(email.trim(), password, false) }) { Text("Connexion") }
                        OutlinedButton(onClick = { vm.cloudLogin(email.trim(), password, true) }) { Text("Créer") }
                    }
                } else {
                    Text("Cloud connecté", color = Accent, fontWeight = FontWeight.Bold)
                    Text("Appareils associés :", color = SecondaryText)
                    vm.cloudDevices.forEach { Text("• ${it.name} · ${if (it.platform == "tv") "TV" else "Mobile"}") }
                    Text("Télécommande", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { vm.sendRemoteControl("pause") }) { Text("Ⅱ") }
                        Button(onClick = { vm.sendRemoteControl("resume") }) { Text("▶") }
                        OutlinedButton(onClick = { vm.sendRemoteControl("stop") }) { Text("■") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::syncCloudNow) { Text("Synchroniser") }
                        OutlinedButton(onClick = vm::cloudLogout) { Text("Déconnecter") }
                    }
                }
                vm.credentials?.let { credentials ->
                    val transferCode = remember(credentials.server, credentials.username) { CredentialTransfer.encode(credentials) }
                    HorizontalDivider()
                    Text("Transférer cette configuration", fontWeight = FontWeight.Bold)
                    Text("Scannez ce QR depuis un autre appareil où Bobcat IPTV est installé.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    QrImage(transferCode)
                }
                vm.localCloudDeviceId?.let { id ->
                    Text("Associer cette box", fontWeight = FontWeight.Bold)
                    QrImage("bobcatiptv://pair/$id")
                }
                vm.cloudMessage?.let { Text(it, color = SecondaryText) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

@Composable
private fun QrImage(value: String) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 240, 240)
        Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 240) for (x in 0 until 240) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = "QR Bobcat", modifier = Modifier.size(220.dp).background(Color.White).padding(6.dp))
}

@Composable
private fun ManualCategorySelectionDialog(vm: IptvViewModel) {
    val data = vm.categorySelectionData ?: return
    var section by remember { mutableStateOf(HomeTab.LIVE) }
    var query by remember { mutableStateOf("") }

    val source = when (section) {
        HomeTab.LIVE -> data.live
        HomeTab.MOVIES -> data.movies
        HomeTab.SERIES -> data.series
        else -> emptyList()
    }
    val selected = when (section) {
        HomeTab.LIVE -> vm.selectedLiveCategoryIds
        HomeTab.MOVIES -> vm.selectedMovieCategoryIds
        HomeTab.SERIES -> vm.selectedSeriesCategoryIds
        else -> emptySet()
    }
    val visible = remember(source, query) {
        if (query.isBlank()) source else source.filter { it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = vm::closeCategorySelection,
        title = { Text(if (vm.categorySelectionConfigured) "Modifier les catégories" else "Choisir les catégories à garder") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    if (vm.categorySelectionConfigured)
                        "Coche ou décoche exactement ce que tu veux afficher. La sélection est mémorisée pour ce serveur."
                    else
                        "Première configuration : aucune catégorie n'est filtrée automatiquement. Coche uniquement celles que tu veux garder.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(HomeTab.LIVE, HomeTab.MOVIES, HomeTab.SERIES), key = { it.name }) { tab ->
                        FilterChip(
                            selected = section == tab,
                            onClick = { section = tab; query = "" },
                            label = { Text(tab.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Rechercher une catégorie") }
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} sélectionnée(s)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.setManualCategorySection(section, visible.map { it.id }.toSet(), true) }) { Text("Tout") }
                    TextButton(onClick = { vm.setManualCategorySection(section, visible.map { it.id }.toSet(), false) }) { Text("Aucun") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 430.dp)) {
                    items(visible, key = { it.id }) { category ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleManualCategory(section, category.id) }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = category.id in selected,
                                onCheckedChange = { vm.toggleManualCategory(section, category.id) }
                            )
                            Text(category.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "TV ${vm.selectedLiveCategoryIds.size} • Films ${vm.selectedMovieCategoryIds.size} • Séries ${vm.selectedSeriesCategoryIds.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
            }
        },
        confirmButton = {
            TextButton(onClick = vm::saveManualCategorySelection) { Text("Enregistrer") }
        },
        dismissButton = {
            if (vm.categorySelectionConfigured) {
                TextButton(onClick = vm::closeCategorySelection) { Text("Annuler") }
            }
        }
    )
}

@Composable
private fun HomeGlobalSearch(vm: IptvViewModel) {
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                vm.searchHomeGlobally(value)
            },
            placeholder = { Text("Rechercher un film ou une série") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PrimaryText,
                unfocusedTextColor = PrimaryText,
                focusedBorderColor = Accent,
                unfocusedBorderColor = OutlineColor,
                focusedPlaceholderColor = SecondaryText,
                unfocusedPlaceholderColor = SecondaryText,
                cursorColor = Accent
            )
        )

        if (query.length < 2) {
            HomeDashboard(vm)
            return@Column
        }

        if (vm.homeSearchLoading) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Accent)
                Spacer(Modifier.width(10.dp))
                Text("Recherche dans films et séries…", color = SecondaryText)
            }
        }

        val total = vm.homeSearchMovies.size + vm.homeSearchSeries.size
        if (!vm.homeSearchLoading && total == 0) {
            EmptyHint("Aucun film ou série trouvé pour « $query »")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.homeSearchMovies.isNotEmpty()) {
                    item {
                        Text("Films (${vm.homeSearchMovies.size})", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    items(vm.homeSearchMovies, key = { "home-movie-${it.id}" }) { movie ->
                        Row(
                            Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(10.dp)).clickable { vm.playMovie(movie) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = movie.icon,
                                contentDescription = null,
                                modifier = Modifier.size(width = 58.dp, height = 82.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(movie.name, color = PrimaryText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Film", color = SecondaryText, style = MaterialTheme.typography.labelMedium)
                                displayRating(movie.rating, movie.rating5)?.let { Text(it, color = SecondaryText, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }

                if (vm.homeSearchSeries.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Séries (${vm.homeSearchSeries.size})", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    items(vm.homeSearchSeries, key = { "home-series-${it.id}" }) { series ->
                        Row(
                            Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(10.dp)).clickable { vm.openSeries(series) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = series.cover,
                                contentDescription = null,
                                modifier = Modifier.size(width = 58.dp, height = 82.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(series.name, color = PrimaryText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Série", color = SecondaryText, style = MaterialTheme.typography.labelMedium)
                                displayRating(series.rating, series.rating5)?.let { Text(it, color = SecondaryText, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(vm: IptvViewModel) {
    LaunchedEffect(Unit) { vm.ensureNewContent() }

    val continueWatching = vm.history.filter {
        it.type != ContentType.LIVE && it.durationMs > 0L && it.progress in 0.02f..0.94f
    }.take(10)
    val recentLive = vm.history.filter { it.type == ContentType.LIVE }.take(10)
    val recommendedMovies = vm.recommendedMovies()
    val recommendedSeries = vm.recommendedSeries()
    val latestMovies = vm.newMovies.take(12)
    val latestSeries = vm.newSeries.take(12)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (latestMovies.isNotEmpty()) {
            item {
                FeaturedMovieHero(latestMovies, vm::playMovie)
            }
        }

        if (vm.newContentLoading && latestMovies.isEmpty() && latestSeries.isEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Chargement des dernières sorties…", color = SecondaryText)
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item { SectionTitle("Continuer à regarder") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(continueWatching, key = { it.key }) { record ->
                        HistoryCard(record) { vm.playFromHistory(record) }
                    }
                }
            }
        }

        if (latestMovies.isNotEmpty()) {
            item { SectionTitle("Dernières sorties cinéma") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(latestMovies, key = { "new-movie-${it.id}" }) { movie ->
                        CompactCard(movie.name, movie.icon, movie.releaseDate ?: movie.year) { vm.playMovie(movie) }
                    }
                }
            }
        }

        if (latestSeries.isNotEmpty()) {
            item { SectionTitle("Dernières sorties séries TV") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(latestSeries, key = { "new-series-${it.id}" }) { show ->
                        CompactCard(show.name, show.cover, show.releaseDate ?: show.year) { vm.openSeries(show) }
                    }
                }
            }
        }

        if (recentLive.isNotEmpty()) {
            item { SectionTitle("Chaînes récentes") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentLive, key = { it.key }) { record ->
                        CompactCard(record.title, record.image, null) { vm.playFromHistory(record) }
                    }
                }
            }
        }

        if (recommendedMovies.isNotEmpty()) {
            item { SectionTitle("Films recommandés") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recommendedMovies, key = { it.id }) { movie ->
                        CompactCard(movie.name, movie.icon, displayRating(movie.rating, movie.rating5)) { vm.playMovie(movie) }
                    }
                }
            }
        }

        if (recommendedSeries.isNotEmpty()) {
            item { SectionTitle("Séries à découvrir") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recommendedSeries, key = { it.id }) { show ->
                        CompactCard(show.name, show.cover, displayRating(show.rating, show.rating5)) { vm.openSeries(show) }
                    }
                }
            }
        }

        if (continueWatching.isEmpty() && recentLive.isEmpty() && latestMovies.isEmpty() && latestSeries.isEmpty()) {
            item {
                EmptyHint("Ton accueil se remplira automatiquement avec tes nouveautés, ton historique et tes lectures.")
            }
        }
    }
}

@Composable
private fun EpgGuideScreen(vm: IptvViewModel) {
    val channels = vm.liveStreams.filterNot { isSeparatorEntry(it.name) }.take(35)
    LaunchedEffect(channels.map { it.id }) { channels.forEach { vm.ensureEpg(it.id) } }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(SurfaceRaised).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Guide des programmes", color = PrimaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRANCE).format(Date()), color = Accent)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(channels, key = { it.id }) { channel ->
                val listings = vm.epgCache[channel.id].orEmpty()
                Row(
                    Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(12.dp)).clickable { vm.playLive(channel) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(model = channel.icon, contentDescription = null, modifier = Modifier.size(48.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(10.dp))
                    Text(channel.name, modifier = Modifier.width(150.dp), color = PrimaryText, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    if (listings.isEmpty()) {
                        LinearProgressIndicator(Modifier.weight(1f), color = Accent)
                    } else {
                        listings.take(3).forEachIndexed { index, item ->
                            Column(
                                Modifier.weight(1f).padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("${formatEpgTime(item.startTimestamp)}–${formatEpgTime(item.endTimestamp)}", color = if (index == 0) Accent else SecondaryText, style = MaterialTheme.typography.labelSmall)
                                Text(item.title, color = PrimaryText, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                if (index == 0) EpgProgress(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedMovieHero(movies: List<VodStream>, onPlay: (VodStream) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val compactPhone = LocalConfiguration.current.screenWidthDp < 600
    var featuredIndex by remember(movies.map { it.id }) { mutableIntStateOf(0) }
    val featured = movies[featuredIndex.coerceIn(movies.indices)]

    LaunchedEffect(movies.map { it.id }) {
        while (true) {
            delay(20_000)
            featuredIndex = (featuredIndex + 1) % movies.size
        }
    }

    val quality = remember(featured.name) {
        when {
            featured.name.contains("4k", ignoreCase = true) || featured.name.contains("uhd", ignoreCase = true) -> "4K UHD"
            featured.name.contains("1080", ignoreCase = true) || featured.name.contains("fhd", ignoreCase = true) -> "Full HD"
            featured.name.contains("720", ignoreCase = true) || featured.name.contains("hd", ignoreCase = true) -> "HD"
            else -> "VOD"
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compactPhone) 250.dp else 320.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(SurfaceRaised, RoundedCornerShape(18.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(featured.icon)
                .crossfade(true)
                .transformations(BlurTransformation(context, radius = 18, sampling = 2))
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(18.dp)))

        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compactPhone) 18.dp else 34.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("NOUVEAUTÉ", color = Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    featured.name,
                    color = PrimaryText,
                    style = if (compactPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(featured.releaseDate ?: featured.year, displayRating(featured.rating, featured.rating5), quality)
                        .joinToString("  •  "),
                    color = SecondaryText,
                    style = MaterialTheme.typography.titleMedium
                )
                if (!compactPhone) Text(
                    "Une des dernières sorties ajoutées à votre catalogue.", color = SecondaryText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onPlay(featured) }, colors = ButtonDefaults.buttonColors(containerColor = AccentStrong)) {
                        Text("▶ Regarder")
                    }
                    if (!compactPhone) OutlinedButton(onClick = { onPlay(featured) }) {
                        Text("▷ Extrait")
                    }
                }
            }
            if (!compactPhone) Spacer(Modifier.width(24.dp))
            if (!compactPhone) AsyncImage(
                model = featured.icon,
                contentDescription = featured.name,
                modifier = Modifier.width(150.dp).fillMaxHeight().padding(vertical = 8.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun HistoryCard(record: PlaybackRecord, onClick: () -> Unit) {
    Column(
        Modifier.width(170.dp).background(SurfaceRaised, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(10.dp)
    ) {
        AsyncImage(
            model = record.image,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(105.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(record.title, color = PrimaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { record.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Accent)
        Text("${(record.progress * 100).toInt()} %", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompactCard(title: String, image: String?, rating: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).background(SurfaceRaised, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(9.dp)
    ) {
        AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(7.dp))
        Text(title, color = PrimaryText, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        rating?.let { Text(it, color = Accent, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = SecondaryText, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CategoryChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                if (selected) "✓ $label" else label,
                maxLines = 1,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
            )
        },
        interactionSource = interactionSource,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (focused) AccentStrong else SurfaceRaised,
            labelColor = PrimaryText,
            selectedContainerColor = Color(0xFFFFD600),
            selectedLabelColor = Color.Black
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (focused) Color.White else OutlineColor,
            selectedBorderColor = Color.White,
            borderWidth = if (focused) 3.dp else 1.dp,
            selectedBorderWidth = 4.dp
        )
    )
}

@Composable
private fun LiveList(vm: IptvViewModel, category: String?, query: String, listState: LazyListState) {
    val list = remember(vm.liveStreams, category, query) {
        vm.liveStreams.asSequence()
            .filter { category == null || it.categoryId == category }
            .filter { query.isBlank() || it.name.contains(query, true) }
            .filterNot { isSeparatorEntry(it.name) }
            .toList()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("${list.size} chaînes", color = SecondaryText, modifier = Modifier.padding(6.dp)) }
        items(list, key = { it.id }) { item ->
            LiveRow(item, vm)
        }
    }
}

@Composable
private fun LiveRow(item: LiveStream, vm: IptvViewModel) {
    LaunchedEffect(item.id) { vm.ensureEpg(item.id) }
    val epg = vm.epgCache[item.id].orEmpty()
    val current = epg.firstOrNull()
    val next = epg.getOrNull(1)
    val favorite = "live:${item.id}" in vm.favorites
    val shape = RoundedCornerShape(12.dp)

    Row(
        Modifier.fillMaxWidth().background(SurfaceRaised, shape).border(1.dp, OutlineColor.copy(alpha = 0.5f), shape)
            .clickable { vm.playLive(item) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = item.icon, contentDescription = null, modifier = Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(item.name, color = PrimaryText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            current?.let {
                Text("Maintenant · ${formatEpgTime(it.startTimestamp)}  ${it.title}", color = Accent, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                EpgProgress(it)
            }
            next?.let {
                Text("Ensuite · ${formatEpgTime(it.startTimestamp)}  ${it.title}", color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { vm.openEpg(item) }) { Text("EPG", color = Accent) }
            TextButton(onClick = { vm.toggleFavorite("live:${item.id}") }) { Text(if (favorite) "★" else "☆", color = Accent) }
        }
    }
}

@Composable
private fun EpgProgress(item: EpgItem) {
    val now = System.currentTimeMillis() / 1000L
    if (item.endTimestamp > item.startTimestamp && item.startTimestamp > 0L) {
        val p = ((now - item.startTimestamp).toFloat() / (item.endTimestamp - item.startTimestamp).toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Accent)
    }
}

private fun formatEpgTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    return SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(millis))
}

private fun isSeparatorEntry(name: String): Boolean {
    val value = name.trim()
    if (value.isBlank()) return true
    if (value.startsWith("###") || value.startsWith("---") || value.startsWith("***")) return true
    return value.count { it.isLetterOrDigit() } < 2
}

@Composable
private fun MovieGrid(vm: IptvViewModel, query: String, categoryId: String) {
    var sortMode by remember { mutableStateOf(MediaSortMode.AZ) }
    val source = if (query.isBlank()) vm.vodStreams else vm.movieSearchResults
    val list = remember(source, query, sortMode) {
        sortMovies(source.filter { query.isBlank() || it.name.contains(query, true) }, sortMode)
    }
    val gridKey = if (query.isBlank()) "movie:$categoryId" else "movie:global-search"
    val gridState = rememberPersistentGridState(gridKey)
    PosterPrefetch(list.map { it.icon })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (vm.movieSearchLoading && query.isNotBlank()) "Recherche globale… ${list.size} résultat(s)" else "${list.size} films",
                color = SecondaryText
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(MediaSortMode.entries, key = { it.name }) { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = { sortMode = mode },
                    label = { Text(if (sortMode == mode) "✓ ${mode.label}" else mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFC107),
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 145.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(list, key = { it.id }) { movie ->
                PosterCard(
                    movie.name,
                    movie.icon,
                    displayRating(movie.rating, movie.rating5),
                    "movie:${movie.id}" in vm.favorites,
                    { vm.toggleFavorite("movie:${movie.id}") },
                    vm.moviePreviewUrl(movie)
                ) { vm.playMovie(movie) }
            }
        }
    }
}

@Composable
private fun SeriesGrid(vm: IptvViewModel, query: String, categoryId: String) {
    var sortMode by remember { mutableStateOf(MediaSortMode.AZ) }
    val list = remember(vm.series, query, sortMode) {
        sortSeries(vm.series.filter { query.isBlank() || it.name.contains(query, true) }, sortMode)
    }
    val gridState = rememberPersistentGridState("series:$categoryId")
    PosterPrefetch(list.map { it.cover })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size} séries", color = SecondaryText)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(MediaSortMode.entries, key = { it.name }) { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = { sortMode = mode },
                    label = { Text(if (sortMode == mode) "✓ ${mode.label}" else mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFC107),
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 145.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(list, key = { it.id }) { show ->
                PosterCard(
                    show.name,
                    show.cover,
                    displayRating(show.rating, show.rating5),
                    "series:${show.id}" in vm.favorites,
                    { vm.toggleFavorite("series:${show.id}") }
                ) { vm.openSeries(show) }
            }
        }
    }
}

@Composable
private fun PosterCard(title: String, image: String?, rating: String?, favorite: Boolean, onFavorite: () -> Unit, previewUrl: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    LaunchedEffect(focused, previewUrl) {
        previewVisible = false
        if (focused && previewUrl != null) {
            delay(1_500)
            previewVisible = true
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .animateContentSize()
            .onFocusChanged { focused = it.isFocused }
            .background(SurfaceRaised, RoundedCornerShape(14.dp))
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else OutlineColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box {
            if (previewVisible && previewUrl != null) {
                InlineMoviePreview(previewUrl, Modifier.fillMaxWidth().aspectRatio(0.72f))
            } else {
                AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(0.72f), contentScale = ContentScale.Crop)
            }
            QualityBadges(title, Modifier.align(Alignment.BottomStart).padding(5.dp))
            TextButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(if (favorite) "★" else "☆", color = Accent, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(title, color = PrimaryText, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        rating?.let { Text(it, color = Accent, style = MaterialTheme.typography.labelMedium) }
        val year = mediaYear(title)
        if (year > 0) Text(year.toString(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InlineMoviePreview(url: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            volume = 1f
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(url))
            seekTo(5 * 60 * 1000L)
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player) {
        delay(20_000)
        player.pause()
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { host ->
            PlayerView(host).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        modifier = modifier
    )
}

@Composable
private fun FavoritesList(vm: IptvViewModel, query: String, listState: LazyListState) {
    val live = vm.liveStreams.filter { "live:${it.id}" in vm.favorites && !isSeparatorEntry(it.name) && (query.isBlank() || it.name.contains(query, true)) }
    val movies = vm.vodStreams.filter { "movie:${it.id}" in vm.favorites && (query.isBlank() || it.name.contains(query, true)) }
    val shows = vm.series.filter { "series:${it.id}" in vm.favorites && (query.isBlank() || it.name.contains(query, true)) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live.isNotEmpty()) item { SectionTitle("TV") }
        items(live, key = { "fl${it.id}" }) { item -> LiveRow(item, vm) }
        if (movies.isNotEmpty()) item { SectionTitle("Films") }
        items(movies, key = { "fm${it.id}" }) { item ->
            MediaRow(item.name, item.icon, displayRating(item.rating, item.rating5), true, { vm.toggleFavorite("movie:${item.id}") }) { vm.playMovie(item) }
        }
        if (shows.isNotEmpty()) item { SectionTitle("Séries") }
        items(shows, key = { "fs${it.id}" }) { item ->
            MediaRow(item.name, item.cover, displayRating(item.rating, item.rating5), true, { vm.toggleFavorite("series:${item.id}") }) { vm.openSeries(item) }
        }
    }
}

@Composable
private fun MediaRow(title: String, image: String?, rating: String?, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(90.dp).background(SurfaceRaised, shape).border(1.dp, OutlineColor.copy(alpha = 0.65f), shape)
            .clickable(onClick = onClick).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = image, contentDescription = null, modifier = Modifier.size(66.dp), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            rating?.let { Text(it, color = Accent, style = MaterialTheme.typography.labelMedium) }
        }
        TextButton(onClick = onFavorite) { Text(if (favorite) "★" else "☆", color = Accent, style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Accent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
}

@Composable
private fun EpgDialog(stream: LiveStream, items: List<EpgItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text(stream.name, color = PrimaryText) },
        containerColor = SurfaceColor,
        text = {
            if (items.isEmpty()) {
                Text("Aucun EPG disponible pour cette chaîne.", color = SecondaryText)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { epg ->
                        Column(Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text("${formatEpgTime(epg.startTimestamp)} – ${formatEpgTime(epg.endTimestamp)}", color = Accent)
                            Text(epg.title, color = PrimaryText, fontWeight = FontWeight.SemiBold)
                            if (epg.description.isNotBlank()) Text(epg.description, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SeriesDialog(vm: IptvViewModel, seriesItem: SeriesItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = {
            Column {
                Text(seriesItem.name, color = PrimaryText)
                displayRating(seriesItem.rating, seriesItem.rating5)?.let { Text(it, color = Accent, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        containerColor = SurfaceColor,
        textContentColor = PrimaryText,
        text = {
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp)) {
                if (vm.seriesLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                } else {
                    val info = vm.seriesInfo
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        info?.info?.plot?.takeIf { it.isNotBlank() }?.let { plot -> item { Text(plot, color = SecondaryText) } }
                        info?.episodes?.toSortedMap(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE })?.forEach { (season, episodes) ->
                            item { SectionTitle("Saison $season") }
                            items(episodes, key = { it.id }) { episode ->
                                Row(
                                    Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(8.dp))
                                        .border(1.dp, OutlineColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable { vm.playEpisode(episode) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("E${episode.episodeNumber}", color = Accent, modifier = Modifier.width(48.dp))
                                    Text(episode.title.ifBlank { "Épisode ${episode.episodeNumber}" }, color = PrimaryText,
                                        modifier = Modifier.weight(1f), maxLines = 2)
                                    Text("▶", color = Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PlayerScreen(
    playable: Playable,
    resumePosition: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onSendToTv: (() -> Unit)? = null,
    remoteAction: Pair<Long, String>? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    var alternateLiveEngine by remember(playable.contentKey) { mutableStateOf(false) }
    var playbackAttempt by remember(playable.contentKey) { mutableIntStateOf(0) }
    val alternateUrl = remember(playable.url) { alternateStreamHost(playable.url) }
    val effectivePlayable = remember(playable, playbackAttempt) {
        if (playbackAttempt >= 3 && alternateUrl != null) playable.copy(url = alternateUrl) else playable
    }

    DisposableEffect(Unit) {
        activity?.let { host ->
            host.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(host.window, false)
            WindowInsetsControllerCompat(host.window, host.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            host.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.let { host ->
                host.requestedOrientation = if (host.isTelevision()) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(host.window, true)
                WindowInsetsControllerCompat(host.window, host.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                host.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
    if (playable.type == ContentType.LIVE) {
        if (!alternateLiveEngine) {
            VlcLivePlayer(playable, onBack, onPrevious, onNext, remoteAction) { alternateLiveEngine = true }
        } else {
            Media3LiveFallbackPlayer(playable, onBack, onPrevious, onNext, remoteAction) { alternateLiveEngine = false }
        }
    } else {
        when (playbackAttempt) {
            0 -> Media3VodPlayer(effectivePlayable, resumePosition, onProgress, onBack, remoteAction) { playbackAttempt = 1 }
            1 -> VlcVodFallbackPlayer(effectivePlayable, resumePosition, onProgress, onBack, remoteAction, hardwareDecode = true, onAutomaticFallback = { playbackAttempt = 2 }) { playbackAttempt = 0 }
            2 -> VlcVodFallbackPlayer(effectivePlayable, resumePosition, onProgress, onBack, remoteAction, hardwareDecode = false, onAutomaticFallback = if (alternateUrl != null) ({ playbackAttempt = 3 }) else null) { playbackAttempt = 0 }
            3 -> Media3VodPlayer(effectivePlayable, resumePosition, onProgress, onBack, remoteAction) { playbackAttempt = 4 }
            else -> VlcVodFallbackPlayer(effectivePlayable, resumePosition, onProgress, onBack, remoteAction, hardwareDecode = false, onAutomaticFallback = null) { playbackAttempt = 0 }
        }
    }
    if (onSendToTv != null && activity?.isTelevision() != true) {
        Button(onClick = onSendToTv, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) { Text("Lire sur la TV") }
    }
    }
}

private fun alternateStreamHost(url: String): String? = when {
    url.contains("vpn.dndnscloud.ru", true) -> url.replace("vpn.dndnscloud.ru", "line.dndnscloud.ru", true)
    url.contains("line.dndnscloud.ru", true) -> url.replace("line.dndnscloud.ru", "vpn.dndnscloud.ru", true)
    url.contains("vpn.ca1-dino.xyz", true) -> url.replace("vpn.ca1-dino.xyz", "line.ca1-dino.xyz", true)
    url.contains("line.ca1-dino.xyz", true) -> url.replace("line.ca1-dino.xyz", "vpn.ca1-dino.xyz", true)
    else -> null
}

@Composable
private fun VlcLivePlayer(
    playable: Playable,
    onBack: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    remoteAction: Pair<Long, String>?,
    onNeedFallback: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var status by remember(playable.url) { mutableStateOf("Ouverture du direct…") }

    val libVlc = remember(playable.url) {
        LibVLC(context, arrayListOf(
            "--network-caching=1500",
            "--live-caching=1500",
            "--http-reconnect",
            "--http-user-agent=IPTVSmartersPlayer/1.1 Android",
            "--avcodec-hw=any"
        ))
    }
    val vlcPlayer = remember(playable.url) { VlcMediaPlayer(libVlc) }
    LaunchedEffect(remoteAction?.first) { when (remoteAction?.second) { "pause" -> vlcPlayer.pause(); "play", "resume" -> vlcPlayer.play(); "stop" -> vlcPlayer.stop() } }

    fun startUrl(url: String) {
        val media = VlcMedia(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1500")
            addOption(":live-caching=1500")
            addOption(":http-reconnect")
            addOption(":http-user-agent=IPTVSmartersPlayer/1.1 Android")
        }
        vlcPlayer.media = media
        media.release()
        vlcPlayer.play()
    }

    DisposableEffect(playable.url) {
        var hlsFallbackUsed = false
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                VlcMediaPlayer.Event.Opening -> mainHandler.post { status = "Connexion VLC…" }
                VlcMediaPlayer.Event.Playing -> mainHandler.post { status = "VLC" }
                VlcMediaPlayer.Event.EndReached,
                VlcMediaPlayer.Event.EncounteredError -> mainHandler.post {
                    if (!hlsFallbackUsed && playable.url.endsWith(".ts", true)) {
                        hlsFallbackUsed = true
                        status = "VLC → HLS…"
                        vlcPlayer.stop()
                        startUrl(playable.url.dropLast(3) + "m3u8")
                    } else {
                        status = "Bascule Media3…"
                        onNeedFallback()
                    }
                }
            }
        }
        startUrl(playable.url)
        onDispose {
            runCatching { vlcPlayer.setEventListener(null) }
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.vlcVout.detachViews() }
            vlcPlayer.release()
            libVlc.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { surface ->
                    surface.keepScreenOn = true
                    surface.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                        val width = (right - left).coerceAtLeast(1)
                        val height = (bottom - top).coerceAtLeast(1)
                        runCatching { vlcPlayer.vlcVout.setWindowSize(width, height) }
                    }
                    vlcPlayer.vlcVout.setVideoView(surface)
                    vlcPlayer.vlcVout.attachViews()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Media3LiveFallbackPlayer(
    playable: Playable,
    onBack: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    remoteAction: Pair<Long, String>?,
    onRetryVlc: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fallbackUrl = remember(playable.url) {
        if (playable.url.endsWith(".ts", true)) playable.url.dropLast(3) + "m3u8" else playable.url
    }
    var failed by remember(playable.contentKey) { mutableStateOf(false) }
    val player = remember(fallbackUrl) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmartersPlayer/1.1 Android")
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("fr", "fra", "fre", "fr-FR", "en")
                    .setPreferredTextLanguages("fr", "fra", "fre", "fr-FR")
                    .build()
                setMediaItem(MediaItem.fromUri(fallbackUrl))
                prepare()
                playWhenReady = true
            }
    }
    LaunchedEffect(remoteAction?.first) { when (remoteAction?.second) { "pause" -> player.pause(); "play", "resume" -> player.play(); "stop" -> player.stop() } }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                CrashReporter.record(
                    context,
                    "ERREUR LECTEUR LIVE MEDIA3",
                    error,
                    "title=${playable.title}\nurl=${fallbackUrl}"
                )
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowSubtitleButton(true)
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (failed) {
            Column(Modifier.align(Alignment.Center).background(Color(0xDD111B29), RoundedCornerShape(12.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Media3 ne peut pas lire ce flux", color = PrimaryText)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetryVlc) { Text("Réessayer avec VLC") }
            }
        }
    }
}


@Composable
private fun Media3VodPlayer(
    playable: Playable,
    resumePosition: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
    remoteAction: Pair<Long, String>?,
    onNeedFallback: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var controllerVisible by remember(playable.url) { mutableStateOf(false) }
    val player = remember(playable.url) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmartersPlayer/1.1 Android")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Accept-Language" to "fr-FR,fr;q=0.9,en;q=0.8",
                "Connection" to "keep-alive"
            ))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("fr", "fra", "fre", "fr-FR", "en")
                    .setPreferredTextLanguages("fr", "fra", "fre", "fr-FR")
                    .build()
                setMediaItem(MediaItem.fromUri(playable.url))
                prepare()
                if (resumePosition > 0L) seekTo(resumePosition)
                playWhenReady = true
            }
    }
    LaunchedEffect(remoteAction?.first) { when (remoteAction?.second) { "pause" -> player.pause(); "play", "resume" -> player.play(); "stop" -> player.stop() } }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                CrashReporter.record(
                    context,
                    "ERREUR LECTEUR VOD MEDIA3",
                    error,
                    "title=${playable.title}\nurl=${playable.url}\nposition=${player.currentPosition}"
                )
                onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
                onNeedFallback()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            player.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowSubtitleButton(true)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controllerVisible = visibility == android.view.View.VISIBLE
                    })
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        if (controllerVisible) {
            Text(
                text = playable.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
            }
}

@Composable
private fun VlcVodFallbackPlayer(
    playable: Playable,
    resumePosition: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
    remoteAction: Pair<Long, String>?,
    hardwareDecode: Boolean,
    onAutomaticFallback: (() -> Unit)?,
    onRetryMedia3: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var failed by remember(playable.contentKey) { mutableStateOf(false) }
    var status by remember(playable.contentKey) { mutableStateOf("VLC de secours…") }
    val libVlc = remember(playable.url) {
        LibVLC(context, arrayListOf("--verbose=2", "--network-caching=3000", "--http-reconnect", "--avcodec-hw=${if (hardwareDecode) "any" else "none"}"))
    }
    val vlcPlayer = remember(playable.url) { VlcMediaPlayer(libVlc) }
    LaunchedEffect(remoteAction?.first) { when (remoteAction?.second) { "pause" -> vlcPlayer.pause(); "play", "resume" -> vlcPlayer.play(); "stop" -> vlcPlayer.stop() } }

    DisposableEffect(playable.url) {
        var resumeApplied = false
        vlcPlayer.setEventListener { event ->
            when (event.type) {
                VlcMediaPlayer.Event.Playing -> mainHandler.post {
                    status = "VLC"
                    failed = false
                    if (!resumeApplied && resumePosition > 0L) {
                        resumeApplied = true
                        runCatching { vlcPlayer.time = resumePosition }
                    }
                }
                VlcMediaPlayer.Event.EncounteredError -> mainHandler.post {
                    CrashReporter.recordWithVlcLog(
                        context,
                        "ERREUR LECTEUR VOD VLC",
                        details = "title=${playable.title}\nurl=${playable.url}\nposition=${vlcPlayer.time}\ndecodage=${if (hardwareDecode) "materiel" else "logiciel"}"
                    )
                    if (onAutomaticFallback != null) {
                        status = "Essai du lecteur suivant…"
                        onAutomaticFallback()
                    } else {
                        failed = true
                        status = "Échec VLC"
                    }
                }
            }
        }
        val media = VlcMedia(libVlc, Uri.parse(playable.url)).apply {
            setHWDecoderEnabled(hardwareDecode, false)
            addOption(":network-caching=3000")
            addOption(":http-reconnect")
            addOption(":http-user-agent=IPTVSmartersPlayer/1.1 Android")
        }
        vlcPlayer.media = media
        media.release()
        vlcPlayer.play()
        onDispose {
            onProgress(vlcPlayer.time.coerceAtLeast(0L), vlcPlayer.length.coerceAtLeast(0L))
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.vlcVout.detachViews() }
            vlcPlayer.release()
            libVlc.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { surface -> vlcPlayer.vlcVout.setVideoView(surface); vlcPlayer.vlcVout.attachViews() } },
            modifier = Modifier.fillMaxSize()
        )
                if (failed) {
            Column(Modifier.align(Alignment.Center).background(Color(0xDD111B29), RoundedCornerShape(12.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Les deux lecteurs ont rencontré un problème", color = PrimaryText)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetryMedia3) { Text("Réessayer Media3") }
            }
        }
    }
}
