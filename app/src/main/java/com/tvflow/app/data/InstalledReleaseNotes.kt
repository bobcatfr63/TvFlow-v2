package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Mises à jour : versionCode replacé au-dessus des anciennes versions TVFlow pour que les mises à jour normales ne soient plus vues comme des downgrades.",
        "Accueil : en-tête TVFlow compact, statut de connexion discret et suppression du faux libellé « Connecté à ok ».",
        "Accueil : nouvel accès Apps séparé sur téléphone et Android TV, sans encombrer le contenu principal.",
        "Accueil mobile : cartes de navigation plus petites pour éviter les éléments coupés en mode paysage.",
        "Réglages : l’organisation des chaînes ouvre sa propre vue au lieu d’écraser les réglages généraux.",
        "Réglages : colonnes rééquilibrées pour éviter les boutons verticaux et textes tronqués.",
        "Films : la fiche sélectionnée reste visible, avec une rangée « À découvrir » plus compacte.",
        "Séries : navigation précédent/suivant masquée pendant la lecture et affichée temporairement à la demande.",
        "Séries TV : flèche Bas ouvre les contrôles d’épisodes quelques secondes puis les masque automatiquement.",
        "TVFlow Pulse, tri par vraie date, synopsis Xtream et fond vidéo aléatoire sont conservés."
    )
}
