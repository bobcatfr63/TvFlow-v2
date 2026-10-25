# TVFlow V2 — base de développement unique

## Référence officielle pour les futurs changements

- Dépôt local Windows : `G:\DevServer\repos\TvFlow-v2`
- Branche de consolidation : `consolidation/tvflow-v2-20261025`
- Tous les nouveaux correctifs et fonctionnalités partent de cette branche, puis sont intégrés via des commits ciblés et testés.
- Les branches historiques d'audit, de recherche IA et d'autonomie restent en lecture/comparaison. Ne jamais fusionner leur totalité aveuglément ni les supprimer sans accord.
- Les répertoires worktree historiques ne servent pas aux prochaines APK officielles.
- Les APK sont construites et testées sur le PC Windows local : pas de CI/runner cloud sans demande expresse.

## Invariants de non-régression

1. Instant TV choisit exclusivement parmi les chaînes TNT disponibles, Retour ouvre l'accueil.
2. Lecture Direct : Media3 et secours automatique libVLC, une seule sortie audio active.
3. Direct : aperçu à la sélection sans clic ; Guide : aperçu immédiat sur sélection.
4. Films et Séries Android TV : grille de cinq colonnes, quatre rangées souhaitées, panneau d'aperçu au focus.
5. Smart Loading / cache / sélection de catégories, favoris, recherche et Groq restent compatibles.
6. L'état des identifiants IPTV et clés API reste local et jamais commité.

## Validation avant publication

1. `:app:testDebugUnitTest` et `:app:assembleDebug` sur PC.
2. Exécuter les tests instrumentés ciblés sur émulateur local, puis tester Direct/TNT/Films/Séries et VOD réelle sur Mi Box ou appareil autorisé.
3. Rechercher les crashs, écrans noirs, pistes audio doublées et régressions de focus.
4. Générer `:app:assembleRelease` et signer avec la clé de publication **existante** (ne pas inventer une nouvelle clé).
5. N'émettre une release GitHub qu'après validation de l'APK signée et du numéro de version. Ne pas remplacer `main` sans validation explicite.

## État initial de la consolidation (25 octobre 2026)

- Commits : `c82c3bc` (Instant TV/VLC/UI), `22b7150` (catalogue/cache), `b84364c` (IA), `4414d63` (app).
- Build debug, tests unitaires, compilation release et deux tests instrumentés du catalogue : réussis.
- Release obtenue actuellement **non signée** ; les tests visuels des aperçus et de la grille restent à valider.
- Les écarts avec `audit/tvflow-v2-20261025`, `feature/tvflow-ai-search-20261019` et `autonomous/validated-cancellation-20261018` doivent être examinés par fonction, sans écraser cette base.
