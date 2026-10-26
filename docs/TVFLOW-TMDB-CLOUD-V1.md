# TVFlow Metadata Cloud — audit et base V1

## Audit du client Android existant

- `TmdbMetadataClient.kt` utilise TMDB directement depuis Android avec un jeton configuré dans `SettingsStore` ; recherche film ou série en français.
- Le client nettoie les titres, compare les résultats et les années, puis récupère synopsis, affiche, note et genres. Ne pas remplacer cet appariement prudent par une recherche serveur naïve.
- Gestion existante des refus 401/403, des limites 429, erreurs réseau et cache négatif de six heures en mémoire.
- Les métadonnées sont persistées dans Room et les paramètres/sauvegardes peuvent inclure une configuration TMDB. Compatibilité à préserver.

## Infrastructure déployée

- Projet Supabase TVFlow : `sxynniwybomzkguzomyn`.
- Table isolée `public.tvflow_tmdb_cache` : clé de recherche, JSON de réponse, dates de récupération et expiration ; index d'expiration.
- RLS activée, aucun accès direct `anon`/`authenticated`, droits réservés au `service_role`.
- Cache vide après création, aucune clé TMDB copiée, aucun appel réel vers TMDB déclenché.

## Avant ouverture du service

1. Développer `tvflow-metadata` Edge Function avec authentification d'appareils issue de TVFlow AI Cloud, quota indépendant et suppression des contenus expirés. Ne pas exposer une API TMDB non authentifiée.
2. Ajouter `TMDB_READ_TOKEN` comme secret serveur via Supabase ; ne jamais l'intégrer dans une APK distribuée.
3. Limiter les réponses au périmètre métadonnées films/séries et ne jamais transmettre des informations d'abonnement Xtream/IPTV.
4. Cache serveur bref (objectif 7 jours, et maximum contractuel 6 mois), avec invalidation ; conserver le cache Room actuel pour la fluidité.
5. Ajouter dans le client Android un mode cloud optionnel sans casser les jetons TMDB locaux ni la recherche locale.
6. Tester les collisions de titres, années, films/séries, synopsis français, TMDB indisponible, 401, 429 et stabilité de l'accueil.
7. Ajouter les mentions TMDB et logo officiels aux crédits TVFlow ; vérifier l'autorisation/licence selon distribution commerciale éventuelle.
8. Garder les usages IA générative Groq et les données TMDB séparés ; vérifier les conditions TMDB avant tout usage des données dans des fonctions IA.

## Validation actuelle

- Audit du client Android : réalisé.
- Création de la table cache isolée Supabase : réalisée.
- Cache vide après création : vérifié.
- Passerelle Edge TMDB, connexion APK et récupération TMDB réelle : **non encore mises en place**.
- Mi Box et version mobile actuellement installées : inchangées.
