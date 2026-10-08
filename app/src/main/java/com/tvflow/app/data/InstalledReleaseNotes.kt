package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Téléphone : l’accueil peut maintenant afficher le même fond vidéo vivant que la Mi Box, avec extraits silencieux de films.",
        "Téléphone : les aperçus vidéo sont limités au Wi‑Fi par défaut pour préserver les données mobiles.",
        "Téléphone : nouveau réglage Aperçus animés accueil mobile avec Désactivé, Wi‑Fi et Toujours.",
        "Téléphone : l’aperçu s’arrête automatiquement dès qu’on fait défiler l’accueil pour économiser batterie et réseau.",
        "Téléphone : l’extrait démarre plus tôt que sur TV afin d’obtenir rapidement une image intéressante.",
        "Accueil : contraste mobile adapté pour garder les textes lisibles au-dessus du fond animé.",
        "Android TV : le comportement existant du fond vidéo aléatoire reste inchangé.",
        "TVFlow Pulse et Mon moment restent disponibles sur téléphone et Android TV."
    )
}
