package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Mises à jour : popup désormais global, affiché quel que soit l’écran ouvert au démarrage.",
        "Mises à jour : plusieurs tentatives automatiques au lancement si GitHub répond trop lentement.",
        "Lecture : Media3 conserve le secours VLC et la validation locale a été repassée sur le builder Windows.",
        "Séries : épisode précédent/suivant disponible dans le lecteur et via les touches média/Page de la télécommande.",
        "Organisation des chaînes : sélection globale des chaînes d’une catégorie, tout masquer et tout afficher.",
        "Films : fiche détaillée avec synopsis, note, date et suggestions de la même catégorie.",
        "Séries : fiche enrichie avec jaquette, synopsis, note, date, saisons, épisodes et suggestions.",
        "Accueil : nouveautés françaises et reprise de lecture restent alimentées depuis le catalogue local."
    )
}
