package com.tvflow.app.ui.navigation

internal enum class NextSection(val route: String, val label: String) {
    Dashboard("dashboard", "Accueil"),
    Live("home", "Direct"),
    Guide("guide", "Guide TV"),
    Movies("library/VOD", "Films"),
    Series("library/SERIES", "Séries"),
    Search("search", "Recherche"),
    Ai("tvflowAi", "IA"),
    Favorites("favorites", "Favoris"),
    Settings("settings", "Réglages")
}

/** Detail, login and playback destinations intentionally have no section navigation. */
internal fun nextSectionForRoute(route: String, mediaType: String? = null): NextSection? =
    if (route == "library/{type}") {
        when (mediaType) {
            "VOD" -> NextSection.Movies
            "SERIES" -> NextSection.Series
            else -> null
        }
    } else NextSection.entries.firstOrNull { it.route == route }
