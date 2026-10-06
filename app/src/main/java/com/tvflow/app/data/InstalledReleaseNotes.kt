package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Films et séries plus compacts sur téléphone pour afficher les jaquettes plus haut.",
        "Organisation des chaînes : sélection multiple, tout sélectionner, masquer ou afficher en masse.",
        "Séries : navigation manuelle vers l’épisode précédent ou suivant depuis le lecteur.",
        "Mises à jour : nouveau pop-up avec version, taille, progression et informations de version.",
        "Filtres France : détection plus robuste des catégories Xtream selon les conventions FR, FRA, VF, VOSTFR, FRENCH et MULTI FR."
    )
}
