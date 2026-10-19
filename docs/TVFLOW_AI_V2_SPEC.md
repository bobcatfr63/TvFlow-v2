# TVFlow AI V2 — Spécification validée le 19/10/2026

## Vision
Intelligence quasi invisible, intégrée aux menus et recherches, sans chatbot ni avatar. Android TV/Mi Box et Android mobile, navigation D-pad complète. L'application ne présente comme disponibles que les contenus effectivement présents dans le catalogue Xtream.

## Recherche et découverte
- Recherche universelle Live/EPG/VOD/séries, et filtres spécialisés par onglet.
- Langage naturel, descriptions approximatives, synopsis, acteurs, réalisateurs, franchises, synonymes, corrections orthographiques, filtres temporels, durée, genres et langues.
- Affinage conversationnel en conservant le contexte, retour aux critères précédents et réinitialisation.
- Index local rapide, résultats progressifs, enrichissement TMDB facultatif; échec réseau non bloquant.
- Regroupement prudent des versions; pas de fusion de titres ambigus; sélection VF et qualité compatible, avec sources alternatives vérifiées à la lecture.
- Français/VF prioritaire; langues audio et sous-titres vérifiées, jamais supposées depuis le titre. Les catégories France, Enfants et Autres restent accessibles.
- Voix: afficher les résultats, ouvrir une fiche par défaut, ou démarrer la lecture sur correspondance certaine selon préférence de l'utilisateur.
- Recherche pendant la lecture sans perturber Media3/libVLC ni les thèmes; ne jamais recouvrir la vidéo d'un thème.

## Personnalisation et confidentialité
- Apprentissage local facultatif (favoris, historique, recherches, goûts), édition et effacement des données, synchronisation inter-appareils seulement sur demande.
- Pulse et Moment: recommandations diversifiées, nouveautés du catalogue, similarités, suite d'épisodes, signaux de tendance TMDB et explication concise fondée sur des faits.
- Aucun secret Xtream, jeton ou clé dans les logs, dépôts, télémétrie ou prompts distants.

## Architecture / validation
- Inspecter les modules réels avant toute modification; privilégier Kotlin, Compose, MVVM, coroutines/Flow, stockage local indexé et WorkManager selon besoin réel.
- Recherche locale d'abord, enrichissement asynchrone avec annulation/debounce et limites réseau; compatibilité faible mémoire.
- Développer exclusivement sur copie/branche isolée du dépôt TvFlow-v2; ne pas effacer les changements locaux existants.
- Tests unitaires de pertinence et cas limites, compilation Gradle sur PC Windows local, tests UI/D-pad/émulateur, lecture longue TV/VOD/séries et non-régression avant APK ou intégration.
- Aucun runner cloud sans demande expresse; ne jamais déclarer un test exécuté sans résultat observé.

## Jalons
1. Fiabiliser l'intention locale, fautes de frappe, langue et classement.
2. Index et recherche universelle progressive.
3. Affinage contextuel discret, menus et D-pad.
4. Enrichissement des métadonnées et doublons sûrs.
5. Recommandations, voix, préférences et tests complets.
