package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Accueil TV : le fond vidéo change maintenant automatiquement toutes les 60 secondes.",
        "Accueil TV : le prochain fond est choisi aléatoirement parmi plusieurs VOD françaises et évite de reprendre immédiatement le même film.",
        "Films : récupération du synopsis Xtream à l’ouverture de la fiche, avec cache local.",
        "Films : fallback TMDb si le fournisseur ne renvoie pas de synopsis et qu’un accès TMDb est configuré.",
        "Réglages TV : organisation des chaînes remaniée pour éviter les boutons écrasés et le texte vertical.",
        "Réglages TV : actions de catégorie déplacées dans un panneau dédié, plus lisible à la télécommande.",
        "Base locale : migration des métadonnées VOD sans supprimer le catalogue existant."
    )
}
