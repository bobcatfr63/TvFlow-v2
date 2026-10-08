package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Mobile : ajout des vraies notifications Android lorsqu’une nouvelle version de TVFlow est disponible.",
        "Mobile Android 13+ : TVFlow demande désormais l’autorisation de notifications au premier lancement après mise à jour.",
        "Mises à jour : vérification immédiate au démarrage puis contrôle automatique en arrière-plan toutes les heures avec WorkManager.",
        "Notifications : une même version n’est signalée qu’une seule fois et toucher la notification ouvre directement TVFlow.",
        "Mises à jour : le popup interne de téléchargement reste disponible en complément de la notification système.",
        "Accueil compact, Apps séparées, organisation des chaînes dédiée et navigation d’épisodes raffinée sont conservés."
    )
}
