package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Accueil : en-tête TVFlow beaucoup plus compact, statut de connexion discret et suppression du faux libellé « Connecté à ok ».",
        "Accueil : nouvel accès Apps séparé, disponible sur téléphone et Android TV, sans encombrer le contenu principal.",
        "Accueil mobile : cartes de navigation plus petites pour éviter les éléments coupés en mode paysage.",
        "Réglages : l’organisation des chaînes ouvre désormais sa propre vue au lieu d’écraser les réglages généraux.",
        "Réglages : répartition des colonnes adaptée aux écrans plus étroits pour éviter les boutons verticaux et textes tronqués.",
        "Films : la fiche sélectionnée reste visible sur téléphone, avec affiche et synopsis adaptés à la hauteur disponible.",
        "Films : la rangée « À découvrir » remplace la grosse barre de suggestions avec des cartes plus petites et raffinées.",
        "Séries : navigation épisode précédent/suivant masquée pendant la lecture, affichée temporairement à la demande.",
        "Séries TV : flèche Bas ouvre les contrôles d’épisodes pendant quelques secondes puis les masque automatiquement.",
        "TVFlow Pulse, tri par vraie date de sortie, synopsis Xtream et fond vidéo aléatoire sont conservés."
    )
}
