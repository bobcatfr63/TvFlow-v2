package com.tvflow.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tvflow.app.boot.BootLiveTarget
import com.tvflow.app.connect.TvFlowConnect
import com.tvflow.app.domain.MediaType
import com.tvflow.app.domain.SeriesItem
import com.tvflow.app.domain.VodItem
import com.tvflow.app.ui.dashboard.DashboardScreen
import com.tvflow.app.ui.home.HomeScreen
import com.tvflow.app.ui.guide.GuideScreen
import com.tvflow.app.ui.library.MediaLibraryScreen
import com.tvflow.app.ui.favorites.FavoritesScreen
import com.tvflow.app.ui.login.DemoScreen
import com.tvflow.app.ui.login.LoginScreen
import com.tvflow.app.ui.player.PlayerScreen
import com.tvflow.app.ui.search.SearchScreen
import com.tvflow.app.ui.search.TvFlowAiScreen
import com.tvflow.app.ui.settings.SettingsScreen
import com.tvflow.app.ui.series.SeriesDetailsScreen
import com.tvflow.app.ui.startup.StartupScreen
import com.tvflow.app.ui.update.GlobalUpdateOverlay
import com.tvflow.app.ui.vod.VodDetailsScreen
import com.tvflow.app.ui.vod.VodPlayerScreen

@Composable
fun TVFlowNavHost(
    bootLiveTarget: BootLiveTarget? = null
) {
    val navController = rememberNavController()
    val pendingTransfer by TvFlowConnect.pendingTransfer.collectAsState()

    fun openLive(streamId: Int, name: String, categoryId: String) {
        navController.navigate("player/$streamId/${Uri.encode(name)}/${Uri.encode(categoryId)}")
    }

    fun openVod(streamId: Int, title: String, extension: String?) {
        navController.navigate("vodPlayer/vod/$streamId/${Uri.encode(title)}/${Uri.encode(extension ?: "_")}")
    }

    fun openVodDetails(item: VodItem) {
        navController.navigate(
            "vodDetails/${item.streamId}/${Uri.encode(item.name)}/${Uri.encode(item.containerExtension ?: "_")}" +
                "?poster=${Uri.encode(item.posterUrl ?: "_")}" +
                "&synopsis=${Uri.encode(item.synopsis ?: "_")}" +
                "&rating=${Uri.encode(item.rating ?: "_")}" +
                "&year=${item.releaseYear ?: -1}" +
                "&month=${item.releaseMonth ?: -1}" +
                "&categoryId=${Uri.encode(item.categoryId)}"
        )
    }

    fun openSeriesDetails(item: SeriesItem) {
        navController.navigate(
            "series/${item.seriesId}/${Uri.encode(item.name)}" +
                "?categoryId=${Uri.encode(item.categoryId)}" +
                "&cover=${Uri.encode(item.coverUrl ?: "_")}" +
                "&plot=${Uri.encode(item.plot ?: "_")}" +
                "&rating=${Uri.encode(item.rating ?: "_")}" +
                "&year=${item.releaseYear ?: -1}" +
                "&month=${item.releaseMonth ?: -1}"
        )
    }

    LaunchedEffect(pendingTransfer) {
        val transfer = pendingTransfer ?: return@LaunchedEffect
        when (transfer.kind) {
            "live" -> transfer.mediaId.toIntOrNull()?.let { streamId ->
                openLive(streamId, transfer.title, transfer.categoryId.orEmpty())
            }
            "vod", "episode" -> {
                val seriesId = transfer.seriesId ?: -1
                navController.navigate(
                    "vodPlayer/${transfer.kind}/${Uri.encode(transfer.mediaId)}/${Uri.encode(transfer.title)}/${Uri.encode(transfer.extension ?: "_")}" +
                        "?seriesId=$seriesId&handoffPositionMs=${transfer.positionMs}"
                )
            }
        }
        TvFlowConnect.consume(transfer)
    }

    NavHost(navController = navController, startDestination = "startup") {
        composable("startup") {
            StartupScreen(
                onLoggedIn = {
                    val target = bootLiveTarget
                    if (target != null) {
                        navController.navigate(
                            "player/${target.streamId}/${Uri.encode(target.name)}/${Uri.encode(target.categoryId)}"
                        ) {
                            popUpTo("startup") { inclusive = true }
                        }
                    } else {
                        navController.navigate("dashboard") { popUpTo("startup") { inclusive = true } }
                    }
                },
                onLoggedOut = { navController.navigate("login") { popUpTo("startup") { inclusive = true } } }
            )
        }
        composable("login") {
            LoginScreen(
                onLoggedIn = { navController.navigate("dashboard") { popUpTo("login") { inclusive = true } } },
                onOpenDemo = { navController.navigate("demo") }
            )
        }
        composable("demo") {
            DemoScreen(
                onBack = { navController.popBackStack() },
                onConfigureAccount = { navController.popBackStack("login", inclusive = false) }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onLive = { navController.navigate("home") },
                onGuide = { navController.navigate("guide") },
                onVod = { navController.navigate("library/${MediaType.VOD.name}") },
                onSeries = { navController.navigate("library/${MediaType.SERIES.name}") },
                onSearch = { navController.navigate("tvflowAi") },
                onSettings = { navController.navigate("settings") },
                onFavorites = { navController.navigate("favorites") },
                onChannel = { channel -> openLive(channel.streamId, channel.name, channel.categoryId) },
                onVodItem = ::openVodDetails,
                onSeriesItem = ::openSeriesDetails,
                onResume = { item ->
                    navController.navigate(
                        "vodPlayer/${item.kind}/${Uri.encode(item.mediaId)}/${Uri.encode(item.title)}/${Uri.encode(item.containerExtension ?: "_")}" +
                            "?seriesId=${item.seriesId ?: -1}"
                    )
                }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onChannel = { channel -> openLive(channel.streamId, channel.name, channel.categoryId) },
                onVod = ::openVodDetails,
                onSeries = ::openSeriesDetails
            )
        }
        composable("home") {
            HomeScreen(onPlay = { channel -> openLive(channel.streamId, channel.name, channel.categoryId) })
        }
        composable("guide") {
            GuideScreen(
                onPlay = { channel -> openLive(channel.streamId, channel.name, channel.categoryId) },
                onDashboard = { navController.navigate("dashboard") { launchSingleTop = true } },
                onSearch = { navController.navigate("search") { launchSingleTop = true } },
                onVod = { navController.navigate("library/${MediaType.VOD.name}") { launchSingleTop = true } },
                onSeries = { navController.navigate("library/${MediaType.SERIES.name}") { launchSingleTop = true } },
                onSettings = { navController.navigate("settings") { launchSingleTop = true } }
            )
        }
        composable("settings") {
            SettingsScreen(
                onAddProfile = { navController.navigate("addProfile") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("addProfile") {
            LoginScreen(onLoggedIn = { navController.popBackStack() })
        }
        composable(
            route = "library/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) {
            MediaLibraryScreen(
                onVodSelected = ::openVodDetails,
                onSeriesSelected = ::openSeriesDetails
            )
        }
        composable(
            route = "vodDetails/{streamId}/{title}/{ext}?poster={poster}&synopsis={synopsis}&rating={rating}&year={year}&month={month}&categoryId={categoryId}",
            arguments = listOf(
                navArgument("streamId") { type = NavType.IntType },
                navArgument("title") { type = NavType.StringType },
                navArgument("ext") { type = NavType.StringType },
                navArgument("poster") { type = NavType.StringType; defaultValue = "_" },
                navArgument("synopsis") { type = NavType.StringType; defaultValue = "_" },
                navArgument("rating") { type = NavType.StringType; defaultValue = "_" },
                navArgument("year") { type = NavType.IntType; defaultValue = -1 },
                navArgument("month") { type = NavType.IntType; defaultValue = -1 },
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val streamId = entry.arguments?.getInt("streamId") ?: return@composable
            val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
            val ext = Uri.decode(entry.arguments?.getString("ext").orEmpty()).takeUnless { it == "_" }
            val poster = Uri.decode(entry.arguments?.getString("poster").orEmpty()).takeUnless { it == "_" }
            val synopsis = Uri.decode(entry.arguments?.getString("synopsis").orEmpty()).takeUnless { it == "_" }
            val rating = Uri.decode(entry.arguments?.getString("rating").orEmpty()).takeUnless { it == "_" }
            val year = entry.arguments?.getInt("year")?.takeIf { it > 0 }
            val month = entry.arguments?.getInt("month")?.takeIf { it in 1..12 }
            VodDetailsScreen(
                title = title,
                posterUrl = poster,
                synopsis = synopsis,
                rating = rating,
                releaseYear = year,
                releaseMonth = month,
                onPlay = { openVod(streamId, title, ext) },
                onBack = { navController.popBackStack() },
                onSuggestion = ::openVodDetails
            )
        }
        composable("tvflowAi") {
            TvFlowAiScreen(onVod = ::openVodDetails, onSeries = ::openSeriesDetails,
                onLive = { channel -> openLive(channel.streamId, channel.name, channel.categoryId) })
        }
        composable("search") {
            SearchScreen(
                onLive = { item -> openLive(item.streamId, item.name, item.categoryId) },
                onVod = ::openVodDetails,
                onSeries = ::openSeriesDetails
            )
        }
        composable(
            route = "series/{seriesId}/{name}?categoryId={categoryId}&cover={cover}&plot={plot}&rating={rating}&year={year}&month={month}",
            arguments = listOf(
                navArgument("seriesId") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
                navArgument("cover") { type = NavType.StringType; defaultValue = "_" },
                navArgument("plot") { type = NavType.StringType; defaultValue = "_" },
                navArgument("rating") { type = NavType.StringType; defaultValue = "_" },
                navArgument("year") { type = NavType.IntType; defaultValue = -1 },
                navArgument("month") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { entry ->
            val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
            val cover = Uri.decode(entry.arguments?.getString("cover").orEmpty()).takeUnless { it == "_" }
            val plot = Uri.decode(entry.arguments?.getString("plot").orEmpty()).takeUnless { it == "_" }
            val rating = Uri.decode(entry.arguments?.getString("rating").orEmpty()).takeUnless { it == "_" }
            val year = entry.arguments?.getInt("year")?.takeIf { it > 0 }
            val month = entry.arguments?.getInt("month")?.takeIf { it in 1..12 }
            SeriesDetailsScreen(
                seriesName = name,
                coverUrl = cover,
                plot = plot,
                rating = rating,
                releaseYear = year,
                releaseMonth = month,
                onPlayEpisode = { episode ->
                    navController.navigate(
                        "vodPlayer/episode/${Uri.encode(episode.id)}/${Uri.encode(episode.title)}/${Uri.encode(episode.containerExtension ?: "_")}?seriesId=${entry.arguments?.getInt("seriesId") ?: -1}"
                    )
                },
                onSuggestion = ::openSeriesDetails
            )
        }
        composable(
            route = "vodPlayer/{kind}/{mediaId}/{title}/{ext}?seriesId={seriesId}&handoffPositionMs={handoffPositionMs}",
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("ext") { type = NavType.StringType },
                navArgument("seriesId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("handoffPositionMs") { type = NavType.LongType; defaultValue = 0L }
            )
        ) {
            VodPlayerScreen(
                onPlayEpisode = { episode, seriesId ->
                    navController.popBackStack()
                    navController.navigate(
                        "vodPlayer/episode/${Uri.encode(episode.id)}/${Uri.encode(episode.title)}/${Uri.encode(episode.containerExtension ?: "_")}?seriesId=$seriesId"
                    )
                }
            )
        }
        composable(
            route = "player/{streamId}/{name}/{categoryId}",
            arguments = listOf(
                navArgument("streamId") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType }
            )
        ) {
            PlayerScreen()
        }
    }

    GlobalUpdateOverlay()
}
