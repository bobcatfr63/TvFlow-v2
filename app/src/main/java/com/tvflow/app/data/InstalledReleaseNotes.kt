package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Nouveau « Mon moment » : choisis ton temps disponible et ton humeur, TVFlow compose automatiquement jusqu’à trois propositions adaptées.",
        "Mon moment : formats 20 min, 45 min, 1 h 30, soirée ou surprise avec ambiances Action, Détente, Frissons, Romance et Famille.",
        "TVFlow Pulse : le profil de goûts peut maintenant être rouvert et modifié directement depuis l’accueil.",
        "TVFlow Pulse : les choix déclarés restent prioritaires mais sont enrichis par les catégories réellement regardées.",
        "TVFlow Pulse : l’écran indique désormais ce que l’application a appris de tes habitudes.",
        "Téléphone : les sélecteurs Pulse et Mon moment passent en deux colonnes pour rester lisibles et tactiles.",
        "Android TV : les mêmes fonctions utilisent une disposition plus large et un focus télécommande dédié.",
        "Accueil mobile : marges et cartes encore réduites pour mieux exploiter les écrans en paysage.",
        "Téléphone et Android TV partagent désormais les mêmes fonctions Pulse et Mon moment avec une présentation adaptée à chaque appareil."
    )
}
