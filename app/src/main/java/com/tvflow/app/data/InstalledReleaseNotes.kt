package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String>
        get() = BuildConfig.RELEASE_NOTES
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::frenchSummary)
            .distinct()
            .take(6)
            .toList()

    private fun frenchSummary(subject: String): String = when {
        "array-shaped VOD info" in subject -> "Compatibilité Xtream améliorée : correction de la récupération des informations des films."
        "store-safe app bundle" in subject -> "Préparation de TVFlow pour Google Play, sans perturber les mises à jour GitHub."
        "film detail composable" in subject -> "Correction de l'affichage des fiches de films."
        "cinematic focus hero" in subject -> "TVFlow CINEMA : nouveaux décors, fiches immersives et reprise mieux présentée."
        "installed release notes" in subject -> "Les nouveautés sont maintenant actualisées à chaque version."
        "mobile long press" in subject -> "Téléphone : aperçu vidéo par appui long, avec arrêt automatique."
        "silent focused film" in subject -> "Aperçus silencieux des films et séries après deux secondes de sélection."
        "remove duplicate continue" in subject -> "Accueil simplifié : suppression d'un bandeau en doublon."
        "sync cast actions" in subject -> "Lecteur : commandes Cast et épisode suivant plus discrètes."
        else -> subject.replaceFirstChar { it.uppercaseChar() }
    }
}
