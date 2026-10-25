# TVFlow AI — déploiement bêta Supabase (25 octobre 2026)

## Infrastructure opérationnelle

- Projet Supabase confirmé : `sxynniwybomzkguzomyn` (`bobcatfr63's Project`), **pas PharmaCabinet**.
- Table `public.tvflow_ai_access` : invitations, empreintes identifiant d'installation, empreinte de session et quotas.
- RLS activée, aucun accès de table aux rôles `anon` / `authenticated`. Aucun jeton ni code en clair dans la table.
- Fonctions SQL réservées au rôle de service : `public.tvflow_ai_activate` (invitation à usage unique), `public.tvflow_ai_reserve` (réservation atomique et quota global).
- Edge Function : `tvflow-ai` (version 1), HTTPS : `https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-ai`.
- `POST /register`: JSON `invite_code`, `installation_id`; retourne `access_token` si l'invitation existe et n'a pas été utilisée.
- `POST /intent`: bearer `access_token`, JSON `query`; quota réservé avant appel Groq, puis réponse `{normalized_query}`.
- Sans clé `GROQ_API_KEY` côté serveur, `/intent` refuse les appels : configuration volontairement fermée.

## Vérifications réellement effectuées

- Table vide, RLS activée, aucun grant direct aux rôles publics : validé.
- Publication fonction `tvflow-ai` : réussie.
- HTTP 400 pour inscription mal formée : validé.
- HTTP 403 pour invitation inexistante : validé.
- **Non testé** : invitation réelle, création de session, quota dépassé, requête Groq réelle, installation d'un collègue.

## Avant une bêta utilisable

1. Configurer `GROQ_API_KEY` comme secret Supabase Edge Function ; ne pas l'inscrire en dur ni dans l'APK.
2. Créer un outil admin local pour émettre un code d'invitation aléatoire, n'enregistrer que son SHA-256 via rôle de service et communiquer le code de façon privée. Éviter de sauvegarder les invitations dans Git.
3. Modifier l'APK (branche `consolidation/tvflow-v2-20261025`) : formulaire minimal de code d'invitation une seule fois, identité locale aléatoire et session chiffrée Android Keystore, migration sans casser l'ancien proxy Groq.
4. Ajouter les tests d'intégration auth / RLS / quotas / annulation, tester sur émulateur et Mi Box, puis tester une installation propre sur un deuxième appareil.
5. Enrichir la protection avant ouverture publique : anti-abus en amont, journalisation minimale, rotation/révocation, quotas multi-appareils et contrôle des coûts.

## Précautions

- `verify_jwt=false` sur la fonction car l'inscription précède l'authentification Supabase ; **les routes appliquent leur propre validation**. Ne pas considérer ce seul contrôle comme une protection suffisante pour une ouverture publique.
- Les RPC de privilège élevé restent non exécutables directement par `anon` / `authenticated`; le rôle de service demeure uniquement côté backend.
- Les autres tables/fonctions des autres projets ne doivent pas être modifiées.
- Le backend est un service d'exécution distant; compilation et tests Android restent sur le PC Windows local.
