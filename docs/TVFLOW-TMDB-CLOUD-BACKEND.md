# TVFlow Metadata Cloud — backend Supabase V2

Déployé dans le projet `sxynniwybomzkguzomyn` uniquement.

## Fonctionnement

- Edge Function `tvflow-metadata`, version 2, endpoint `https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-metadata`.
- `POST` JSON `{"type":"movie"|"tv","query":"titre"}`, `Authorization: Bearer <session TVFlow AI Cloud>`.
- Validation de la session via `tvflow_tmdb_authorize`, uniquement accès actif/non expiré ; quota TMDB indépendant (100 demandes/appareil/jour par défaut), sans consommer les appels Groq.
- Requête de recherche TMDB en français, réponse bornée à 15 résultats, cache dédié de 7 jours sur `tvflow_tmdb_cache` ; aucun mot de passe ni catalogue IPTV dans le cache.
- Secret requis côté Edge Function : `TMDB_READ_TOKEN`, jeton *read access token* v4 de TMDB. Ne jamais l'intégrer dans l'APK ou GitHub.
- Tables `tvflow_tmdb_cache` et `tvflow_tmdb_daily_usage` isolées : aucune permission `anon`/`authenticated`; permissions de service dédiées.
- Aucune intégration Android ou AI Manager réalisée pour le moment ; conserver la logique locale existante et l'appariement titre/année.

## Vérification actuelle

- Fonction déployée et active.
- Refus HTTP 401 sans autorisation, HTTP 400 avec type invalide.
- Droits sur les deux tables inspectés : uniquement `service_role`.
- La recherche TMDB de bout en bout **reste à valider** avec `TMDB_READ_TOKEN` configuré et un appareil autorisé. Aucun résultat TMDB réel confirmé.
- La Mi Box et l'APK téléphone ne sont pas modifiées.

## Conditions TMDB

- Attribution TMDB visible dans l'application (logo approuvé et mention de non-affiliation).
- Respecter les limites contractuelles de mise en cache (pas plus de 6 mois ; notre TTL : 7 jours).
- Attention : les conditions API TMDB interdisent certains usages avec des applications ML/IA, notamment l'entraînement. Garder les métadonnées séparées de Groq et examiner les droits avant tout traitement par IA.
- Usage gratuit sous conditions non commerciales ; licence commerciale à vérifier avant distribution monétisée.
