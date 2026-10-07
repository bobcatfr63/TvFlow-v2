package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String> = listOf(
        "Séries TV : accès télécommande au bouton Épisode suivant renforcé, la flèche Bas libère maintenant explicitement le focus du lecteur avant de cibler Suivant.",
        "TVFlow Pulse : profil de goûts au premier lancement avec Action, Romance, Thriller, Science-fiction, Comédie et d’autres univers.",
        "TVFlow Pulse : les suggestions évoluent selon les catégories réellement regardées, en complément des choix de départ.",
        "Accueil : rails Films pour toi et Séries pour toi alimentés par Pulse.",
        "Séries : compatibilité avec les serveurs Xtream qui renvoient le champ info d’un épisode sous forme de tableau au lieu d’un objet.",
        "Films/Séries TV : panneau d’aperçu dans la colonne gauche avec affiche, date, note et synopsis.",
        "Films : tri par vraie date de sortie et enrichissement Xtream/TMDb.",
        "Accueil TV : fond vidéo aléatoire renouvelé automatiquement sans répétition immédiate."
    )
}
