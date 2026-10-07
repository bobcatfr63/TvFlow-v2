package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "TVFlow Pulse : nouveau profil de goûts au premier lancement avec Action, Romance, Thriller, Science-fiction, Comédie et d’autres univers.",
        "TVFlow Pulse : les suggestions évoluent ensuite selon les catégories réellement regardées, en complément des choix de départ.",
        "Accueil : nouveaux rails Films pour toi et Séries pour toi quand Pulse dispose de suffisamment de signaux.",
        "Séries : compatibilité renforcée avec les serveurs Xtream qui renvoient le champ info d’un épisode sous forme de tableau au lieu d’un objet.",
        "Films/Séries TV : panneau d’aperçu dans la colonne gauche avec affiche, date, note et synopsis.",
        "Films : tri par vraie date de sortie et enrichissement des métadonnées Xtream/TMDb.",
        "Séries TV : bouton Épisode suivant accessible à la télécommande avec la flèche Bas.",
        "Accueil TV : fond vidéo aléatoire renouvelé automatiquement sans répétition immédiate."
    )
}
