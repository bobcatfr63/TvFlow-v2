package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Films/Séries TV : nouveau panneau d’aperçu dans la colonne gauche avec affiche, date, note et synopsis du contenu ciblé.",
        "Films : tri des sorties récentes corrigé à partir de la vraie date Xtream, avec enrichissement des premiers éléments visibles.",
        "Films : si la date manque dans Xtream, l’année du titre reste utilisée en secours local.",
        "Séries TV : le bouton Épisode suivant est maintenant directement accessible à la télécommande depuis le lecteur avec la flèche Bas.",
        "Séries : boutons Précédent et Suivant ajoutés directement dans le lecteur pour ne plus dépendre du bouton interne Media3.",
        "Accueil TV : le fond vidéo change automatiquement toutes les 60 secondes et évite de reprendre immédiatement le même film.",
        "Films : synopsis récupéré via get_vod_info puis mis en cache local, avec fallback TMDb si nécessaire.",
        "Réglages TV : organisation des chaînes remaniée pour une navigation plus lisible à la télécommande."
    )
}
