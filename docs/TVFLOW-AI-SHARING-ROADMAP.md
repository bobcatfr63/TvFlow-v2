# TVFlow AI — distribution progressive (option C)

## Décision

Phase 1 : bêta privée pour 5 à 10 collègues, accès par invitation. Phase 2 : ouverture publique après validation réelle des coûts, sécurité et comportements Android TV / mobile. La recherche locale reste toujours accessible et gratuite, même si la passerelle distante est indisponible.

## État existant vérifié

- `AiGatewayConfig` stocke un jeton proxy chiffré par Android Keystore, distinct de la clé Groq.
- `GroqIntentClient` tente la passerelle, puis la recherche se poursuit localement en cas d'échec.
- `scripts/groq_proxy.py` dispose d'un quota global, mais d'un **jeton partagé**, impropre à une diffusion multi-utilisateur.
- L'APK actuelle ne crée aucun accès individuel automatiquement. Aucune clé Groq ne doit être embarquée.

## Architecture retenue

1. Backend HTTPS dédié TVFlow AI, hébergé et nommé explicitement (ne pas réutiliser un projet Supabase non vérifié).
2. Seul le backend possède `GROQ_API_KEY`, via secrets d'exécution. L'APK contient uniquement l'URL publique de la passerelle et une clé publique d'application si nécessaire.
3. Enregistrement d'installation via invitation limitée et expirante. Le backend émet une session courte et renouvelable, liée à un identifiant d'installation aléatoire; stocker uniquement le minimum, sous Android Keystore. Ne jamais envoyer les identifiants Xtream ou le catalogue complet.
4. L'API `POST /v1/intent` reçoit uniquement le texte de recherche strictement nécessaire, refuse les requêtes trop longues et applique des quotas atomiques par installation + globaux, des délais réseau, une taille de réponse maximale, des restrictions de modèle, journalisation sans texte utilisateur ni secrets, et révocation de session.
5. Inscription privée : invitation administrateur pour 5–10 personnes; aucune promesse d'accès anonyme libre sans garde-fous. Les jetons individuels ne sont pas un rempart suffisant contre les abus.
6. Déploiement public seulement après contrôle des coûts réels, tests d'abus, disponibilité du backend et politique de confidentialité explicite. Limite globale stricte des requêtes, réponse 429/503 => secours local immédiat.

## Compatibilité et migration

- Ne pas casser les réglages Groq existants : les installations déjà configurées doivent continuer en mode legacy pendant la transition.
- Préférer une interface `AiGatewayAuth` séparant explicitement la session d'installation et l'ancien jeton de passerelle; aucun jeton global embarqué dans `BuildConfig`.
- Prévoir des indicateurs non trompeurs : `Recherche locale`, `Groq actif`, `Groq indisponible` et compteur local, distingué des quotas serveur.
- Tester plusieurs installations indépendantes sur TV et téléphone, un code invitation réutilisé, un code expiré, un accès révoqué, une perte de connexion, la saturation du quota et la réinstallation.

## Déploiement

- Les builds et tests Android utilisent uniquement le PC Windows local; pas de runner cloud.
- Le backend hébergé est un service d'exécution, pas un runner de compilation.
- Ne pas toucher à `main` ou à une release officielle avant signature avec la clé existante et validation VOD/Direct.
- Supabase: **projet TVFlow à identifier explicitement** avant toute création de tables, fonctions ou secrets; aucun projet existant ne doit être supposé TVFlow uniquement sur son nom.

## Critères de validation bêta

- APK neuve : accès Groq après activation via une invitation, sans clé API Groq ni configuration d'URL par l'utilisateur.
- Le refus ou la panne du backend laisse l'application et la recherche locale opérationnelles.
- Aucun secret Groq, jeton maître, identifiant IPTV, ni donnée de catalogue n'est commité ou embarqué dans l'APK.
- Vérification des quotas/permissions côté serveur, pas dans l'APK seule.
- Tests automatisés + usage réel Mi Box, téléphone et installation d'un collègue avant élargissement.
