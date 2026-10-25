# TVFlow AI Manager — première version déployée

## Accès

URL : https://sxynniwybomzkguzomyn.supabase.co/functions/v1/tvflow-ai-manager

Fonction Supabase `tvflow-ai-manager` (v1). Page web avec connexion via Supabase Auth email/mot de passe et gestion d'invitations. Le projet TVFlow reste `sxynniwybomzkguzomyn`.

## Sécurité

- Le navigateur utilise uniquement la clé **publishable** Supabase, jamais le secret service_role ni la clé Groq.
- Les requêtes d'administration exigent un Bearer token validé par Supabase Auth `/auth/v1/user` **et** une appartenance explicite à `public.tvflow_ai_admins`.
- Cette liste est vide initialement ; personne ne peut créer de code tant que son utilisateur Auth n'est pas inscrit dans l'allowlist.
- Tables `public.tvflow_ai_admins` et `public.tvflow_ai_access` protégées par RLS et privilèges restreints ; seules les fonctions backend accèdent aux données via service_role.
- Code d'invitation aléatoire généré par Web Crypto, hash SHA-256 uniquement en base, affiché une fois après création.
- Quota par invitation 1 à 50 requêtes/jour et validité 1 à 30 jours.

## Mise en service administrateur

1. Vérifier qu'un utilisateur **Supabase Auth** email/mot de passe existe pour l'administrateur TVFlow (la connexion au dashboard Supabase n'est pas automatiquement un utilisateur Supabase Auth).
2. Après validation de son identité et de l'adresse email, inscrire uniquement son UUID dans `public.tvflow_ai_admins`, jamais par auto-inscription depuis la page web. SQL d'exemple : `insert into public.tvflow_ai_admins(user_id) values ('UUID_DU_COMPTE_AUTH');`.
3. Se connecter depuis la page TVFlow AI Manager, générer une invitation et vérifier son activation sur un appareil TVFlow.

## Tests réalisés

- GET page : HTTP 200, titre présent.
- POST création sans authentification : HTTP 401.
- Compte administrateur réel et création/révocation depuis la page : non testés (liste blanche vide).

Compilation Android et APK restent exclusivement sur PC Windows local ; pas de runner cloud.
