package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String>
        get() = BuildConfig.RELEASE_NOTES
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::frenchSummary)
            .distinct()
            .take(6)
            .toList()

    /** Shared human-readable summaries for installed notes and available-update dialogs. */
    fun localizedItems(rawNotes: List<String>): List<String> = rawNotes
        .map(::frenchSummary)
        .distinct()
        .take(4)

    private fun frenchSummary(subject: String): String = when {
        "unified TV and mobile favorites library" in subject -> "Favoris : nouvel écran commun pour retrouver chaînes, films et séries sur mobile et TV."
        "save VOD and series favorites per profile" in subject -> "Favoris : films et séries enregistrés séparément pour chaque profil IPTV."
        "toggle channel favorites from channel list" in subject -> "Favoris : ajout ou retrait d'une chaîne directement depuis la liste."
        "filter requested genres before ranking" in subject -> "TVFlow AI : genres respectés et recherches par catégories accélérées sur les gros catalogues."
        "compact global search index" in subject -> "TVFlow AI : recherche plus économe en mémoire sur les catalogues volumineux."
        "balance AI candidates across IPTV categories" in subject -> "TVFlow AI : suggestions mieux réparties entre les catégories IPTV sur les gros catalogues."
        "discover AI candidates from provider categories" in subject -> "TVFlow AI : recherche par genre élargie aux catégories IPTV, même sans mots-clés dans les titres."
        "avoid repeat TMDB genre enrichment" in subject -> "TMDB : moins de recherches répétées, progression sur davantage de fiches."
        "backfill TMDB genres on existing titles" in subject -> "TMDB : genres ajoutés aussi aux fiches ayant déjà un synopsis, sans appels Xtream inutiles."
        "show TMDB genre coverage" in subject -> "TMDB : couverture des genres mesurée pour films et séries dans les réglages."
        "persist TMDB genre IDs" in subject -> "TVFlow AI : genres TMDB enregistrés et pris en compte dans les recommandations."
        "rank AI results by catalog categories" in subject -> "TVFlow AI : classement amélioré par genres des catégories IPTV."
        "improve compound genre intent" in subject -> "TVFlow AI : recherches action et aventure mieux ciblées, affichage des séries clarifié."
        "avoid duplicate update popup" in subject -> "Mises à jour : une seule fenêtre proposée, sans doublon sur l'accueil."
        "add TMDB outcome diagnostics" in subject -> "TMDB : diagnostics détaillés par cause dans les réglages."
        "clean IPTV titles for TMDB" in subject -> "TMDB : meilleure reconnaissance des titres IPTV, sous-titres préservés."
        "improve TMDB lookup reliability" in subject -> "TMDB : recherche par titre original et réduction des requêtes inutiles."
        "clarify Pulse and TMDB labels" in subject -> "Accueil : Pulse et statistiques TMDB expliqués plus clairement."
        "stable semantic Android versionCode" in subject -> "Mises à jour : numéro Android corrigé pour éviter les versions bloquées sur GitHub."
        "official TNT channel order" in subject -> "TNT : ordre officiel des chaînes conservé dans le Direct et le Guide TV."
        "fix TNT category filtering" in subject -> "TNT : catégorie plus fiable, doublons réduits et chaînes masquées respectées."
        "similar films from catalog" in subject -> "TVFlow AI : suggestions de films similaires basées sur ton catalogue et leurs synopsis."
        "understand natural French AI requests" in subject -> "TVFlow AI : meilleure compréhension du français naturel et suggestions mieux ciblées."
        "improve AI ranking" in subject -> "TVFlow AI : recherches plus pertinentes, moins de faux résultats et priorité aux vrais titres."
        "simplify Guide categories" in subject -> "Guide TV : suppression de Toutes les chaînes, ouverture sur TNT et accès direct aux favoris."
        "TNT category and rainbow animation" in subject -> "Nouvelle catégorie TNT dans le Guide et le Direct ; effet lumineux Rainbow animé amélioré."
        "compact AI results and extend rainbow focus" in subject -> "TVFlow AI : résultats plus visibles et bordures Rainbow ajoutées à la recherche."
        "rainbow" in subject.lowercase() -> "Nouveau thème Arc-en-ciel : contours lumineux animés autour des onglets actifs."
        "increment" in subject.lowercase() && "version" in subject.lowercase() -> "Le numéro de version évolue désormais à chaque publication."
        "correct TNT and alphabetical channel sorting" in subject -> "Direct et Guide : ordre TNT corrigé et classement A à Z plus fiable, sans les préfixes IPTV."
        "release date filters across all movie and series categories" in subject -> "Films et séries : les filtres par année et par mois couvrent toutes les catégories."
        "advance TVFlow version code" in subject -> "Mises à jour : installation corrigée sur les téléphones disposant d'un numéro de build supérieur."
        "share canonical TVFlow release manifest" in subject || "canonical release manifest for update notifications" in subject -> "Mises à jour : détection et notifications réunies sur une seule source fiable."
        "refresh AI TV layout and optimize catalog search" in subject -> "TVFlow AI : nouvelle grille de suggestions et recherche plus rapide dans les grands catalogues."
        "array-shaped VOD info" in subject -> "Compatibilité Xtream améliorée : correction de la récupération des informations des films."
        "store-safe app bundle" in subject -> "Préparation de TVFlow pour Google Play, sans perturber les mises à jour GitHub."
        "film detail composable" in subject -> "Correction de l'affichage des fiches de films."
        "cinematic focus hero" in subject -> "TVFlow CINEMA : nouveaux décors, fiches immersives et reprise mieux présentée."
        "installed release notes" in subject -> "Les nouveautés sont maintenant actualisées à chaque version."
        "mobile long press" in subject -> "Téléphone : aperçu vidéo par appui long, avec arrêt automatique."
        "silent focused film" in subject -> "Aperçus silencieux des films et séries après deux secondes de sélection."
        "remove duplicate continue" in subject -> "Accueil simplifié : suppression d'un bandeau en doublon."
        "sync cast actions" in subject -> "Lecteur : commandes Cast et épisode suivant plus discrètes."
        "balance movies and series across release eras" in subject -> "Pulse et Mon moment proposent désormais des films et séries de toutes les époques."
        "integrate Google Play billing" in subject -> "Préparation du paiement Premium et de la restauration des achats Google Play."
        "prepare lifetime offer" in subject -> "Préparation de TVFlow Premium, disponible à terme par achat unique."
        "premium" in subject.lowercase() -> "Améliorations de l'expérience TVFlow Premium."
        // Preserve release notes supplied by the signed release manifest, even when a
        // new feature has no dedicated translation yet. Never replace real notes with
        // the same generic sentence on every release.
        else -> subject.trim().removePrefix("•").trim().replaceFirstChar { it.uppercaseChar() }
    }
}
