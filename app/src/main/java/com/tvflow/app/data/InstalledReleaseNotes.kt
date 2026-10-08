package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Téléphone : correction des onglets de l’accueil dont les titres étaient coupés en mode paysage.",
        "Téléphone : les cartes de navigation ont désormais une hauteur suffisante et des titres sur deux lignes maximum.",
        "Téléphone : les sous-titres des onglets ont été retirés de l’accueil mobile pour gagner de la place et améliorer la lisibilité.",
        "Téléphone : icônes et typographies rééquilibrées pour éviter tout rognage sur les écrans compacts.",
        "Android TV : aucun changement de taille des tuiles TV, la correction est limitée à l’interface mobile.",
        "Les aperçus animés de l’accueil mobile introduits en 1.0.77 sont conservés."
    )
}
