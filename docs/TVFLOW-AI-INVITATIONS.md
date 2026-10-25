# TVFlow AI Cloud — invitations bêta

Gestionnaire local Windows : `scripts/tvflow_ai_invites.py` (bibliothèque standard Python, aucune dépendance).

## Sécurité

- Ce script est un outil d'administration **sur le PC Windows uniquement**. Ne jamais le distribuer avec l'APK.
- L'URL Supabase est limitée au projet TVFlow `sxynniwybomzkguzomyn` afin de ne jamais modifier une autre application.
- Ne jamais inscrire la clé de rôle `service_role` dans un fichier, un commit Git, une capture d'écran ou l'APK. La conserver temporairement dans les variables d'environnement d'une session locale sécurisée; l'effacer après utilisation.
- Si la clé de rôle n'est pas disponible, créer les invitations directement via l'interface d'administration autorisée ou l'outil de gestion Supabase connecté : ne pas contourner les contrôles.
- Les codes d'invitation aléatoires sont affichés **une fois** lors de leur création. La base ne conserve que leur SHA-256.
- Les invitations sont limitées à 1–30 jours et 1–50 requêtes par installation/jour; quota global serveur appliqué séparément.

## Commandes

Configurer dans la session PowerShell (sans les enregistrer dans les scripts) :

`$env:TVFLOW_SUPABASE_URL='https://sxynniwybomzkguzomyn.supabase.co'`

`$env:TVFLOW_SUPABASE_SERVICE_ROLE_KEY = <valeur secrète, saisie localement>`

Puis :

- `python scripts/tvflow_ai_invites.py create --days 7 --limit 15`
- `python scripts/tvflow_ai_invites.py list`
- `python scripts/tvflow_ai_invites.py revoke --id <UUID>`

Dans TVFlow : **Réglages → TVFlow AI 2.0 → Accès TVFlow AI Cloud → Code d'invitation → Activer**.

## Validation

- Tests locaux des paramètres invalides et de l'absence d'identifiants admin : réussis.
- Tests réels antérieurs : invitation → Android → passerelle → Groq, puis révocation → refus.
- Tests manuels du gestionnaire avec clé administrateur : **non réalisés** (clé absente de la session, ne pas l'exposer).
- Tests multi-installations et ergonomie TV/téléphone : à réaliser avant distribution aux collègues.
