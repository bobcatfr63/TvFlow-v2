package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Récupération TV : nouveau bouton Ouvrir les réglages Android directement depuis TVFlow.",
        "Launcher TV : contourne le cas où Changer d’écran d’accueil relance simplement TVFlow sur certaines box.",
        "Classement : règles France/Enfants/Autres renforcées et corrections mémorisées par profil serveur.",
        "Séries : épisode suivant automatique avec compte à rebours configurable et préchauffage de l’URL suivante.",
        "Historique : ajout de Récemment regardé et marquage Vu par épisode.",
        "Favoris : synchronisation locale TV/téléphone sur le même réseau pour un même profil Xtream.",
        "Recherche : index local mis en cache pour accélérer la recherche globale sur les gros catalogues.",
        "Lecture : diagnostic Media3 conservé avec bascule automatique vers VLC quand nécessaire."
    )
}
