package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Classement : règles France/Enfants/Autres renforcées et corrections mémorisées par profil serveur.",
        "Séries : épisode suivant automatique avec compte à rebours configurable dans les réglages.",
        "Séries : l’URL de l’épisode suivant est préchauffée pour réduire le délai de transition.",
        "Historique : ajout de Récemment regardé et mémorisation durable des lectures terminées.",
        "Séries : les épisodes terminés sont maintenant marqués Vu dans la fiche de la série.",
        "Favoris : synchronisation locale TV/téléphone sur le même réseau pour un même profil Xtream.",
        "Recherche : index local mis en cache pour accélérer la recherche globale sur les gros catalogues.",
        "Lecture : les échecs Media3 restent diagnostiqués et basculent automatiquement vers le secours VLC quand nécessaire."
    )
}
