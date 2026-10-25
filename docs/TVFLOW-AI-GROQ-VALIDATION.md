# TVFlow AI Groq — validation du 23 octobre 2026

Statut : intégration sécurisée et tests locaux réalisés ; validation Groq réelle
inachevée. Ne pas publier officiellement cette APK.

## Contexte

PC DESKTOP-8VD83BL, projet G:\DevServer\repos\TvFlow-v2.
Base Git b7ff1b972b386acfddf7aa2a3ef4c0f423bbe753.
Modifications laissées dans le répertoire de travail : aucun commit ni push,
aucun runner cloud, aucune publication de release.
Compilation sur Windows avec JBR 25.0.3, cible Java 17, Gradle 9.6 / AGP 9.4
et versions Kotlin/Hilt existantes.

## Modifications effectuées

- Proxy Python limité à 127.0.0.1:8765 et authentifié par jeton distinct de Groq.
- Modèle fixe openai/gpt-oss-20b ; aucune activation de facturation.
- Budget SQLite atomique persistant : 50 tentatives/jour UTC, 6/minute,
  7 000 jetons réservés/minute, 100 000/jour. Les échecs consomment une réservation.
- Réservation conservatrice des jetons ; cache mémoire de 256 recherches pendant 1 h.
- Une requête Groq simultanée, 8 connexions entrantes maximum, réponses bornées,
  redirections interdites, temporisation de 60 s après erreur.
- Client Android OkHttp avec limite totale HTTP de 4,5 s, annulation,
  temporisation d'échec de 30 s et délai de 600 ms pour éviter les appels pendant
  la frappe. Une annulation ne bloque plus la recherche suivante.
- Paramétrage TV/téléphone ; jeton de passerelle chiffré par Android Keystore.
  L'application refuse les clés Groq dans ce champ.
- Les critères de la recherche d'origine restent prioritaires ; Groq ajoute
  uniquement des pistes de recherche dans le catalogue. Aucun objet jouable
  ni résultat de disponibilité ne peut être fourni par Groq.
- Similarité, acteurs et EPG conservent leurs chemins dédiés.
- Scripts de saisie masquée, stockage DPAPI et lancement ; aucun secret Groq
  dans Git, les sorties de tests ou l'APK inspectée.
- Correction de 38 erreurs Lint préexistantes : désucrage Java pour API 24–25,
  permission de notification, opt-in Media3 et LocalConfiguration Compose.

## Vérifications réellement exécutées

| Vérification | Résultat |
| --- | --- |
| Tests Python proxy : HTTP, authentification, cache, quotas persistants/concurrents, changement de jour, erreurs simulées | 17 réussis |
| Tests unitaires Android | 121 réussis, 0 échec |
| Lint Debug final | Réussi, 0 erreur ; avertissements existants conservés |
| Intégration sur émulateur TV Android 16 | 8 réussis |
| Intégration sur émulateur téléphone Android 15 | 8 réussis |
| Annulation suivie d'une nouvelle requête | Réussie sur les deux émulateurs |
| Serveur bloqué / limite de durée HTTP | Réussi sur les deux émulateurs |
| Recherche locale « une comedie francaise » | Films du catalogue affichés |
| Recherche locale « une serie policiere » | Séries du catalogue affichées |
| Recherche locale « film action aventure 2025 » | Aucun résultat confirmé ; pertinence non validée positivement |
| Recherche locale « un film comme Interstellar » | Référence ou synopsis indisponible ; fonctionnalité non validée positivement |
| Recherche volontairement inexistante | Aucun résultat confirmé, aucun titre inventé affiché |
| Télécommande | Bas vers recherche vocale et retour vers accueil vérifiés ; parcours complet non audité |
| Lecture VOD réelle | Image inspectée ; Media3 READY, playing=true, progression 0 → 13,683 → 28,713 s |
| Direct IPTV réel | Chaîne 6ter HEVC ouverte ; image inspectée |
| Groq réel | Non exécuté : nouvelle clé locale absente |
| Quota Groq consommé par cette mission | 0 requête réelle, 0 jeton |
| Latence Groq réelle / quota restant du compte | Non mesurés / non consulté |

Les temps UI observés lors des deux premières recherches locales étaient
environ 7,9 s et 10,7 s. Les trois recherches suivantes ont été observées terminées
en 2,2–2,4 s. Ces mesures incluent l'attente du relevé UI et ne sont ni des
mesures précises du moteur seul ni des latences Groq.

Les tests instrumentés utilisent un vrai serveur HTTP local simulant la réponse
de la passerelle. Les tests Python simulent uniquement l'amont Groq.
Ils ne prouvent pas un échange Android → Groq réel.

## Incidents et récupération

Le démarrage graphique de l'émulateur TV a échoué (OpenGL/ColorBuffer).
Le démarrage sans fenêtre, sans snapshot, avec SwiftShader a fonctionné.
Les anciens journaux Java signalaient des erreurs mémoire ; aucune erreur mémoire
n'a été rencontrée pendant les compilations de cette mission.

Le lanceur Gradle connectedDebugAndroidTest a désinstallé l'application de
l'émulateur TV après les tests, supprimant sa clé Android Keystore IPTV.
Une sauvegarde des données avait été faite avant installation (environ 60 Mo).
Catalogue et réglages ont été restaurés depuis cette sauvegarde. Les identifiants
ont été rétablis et rechiffrés à partir de TVFlow-backup (3).tvfb : le compte a
été comparé à l'identifiant stable d'origine et correspond.
La source de sauvegarde n'a pas été modifiée.
Le helper temporaire de récupération a été supprimé du projet et les APK
d'instrumentation ont été désinstallées. Les tests finaux ont été exécutés
manuellement avec ADB, sans désinstaller l'application cible.
Après les tests finaux et un redémarrage à froid, l'accueil affiche bien
« Connecté ». Les deux émulateurs démarrés pour la mission ont été arrêtés.

Sauvegardes PC :
G:\DevServer\backups\tvflow-groq-20261023-213916
(source-before.zip et emulator-tv-app-before.tar).
La sauvegarde de récupération y est protégée par les ACL du compte Windows.

## Compilation et APK

Dernière passe complète : BUILD SUCCESSFUL en 1 min 41 s.
Tests instrumentés finaux : 8/8 sur chaque émulateur.

APK de validation uniquement :
G:\DevServer\repos\TvFlow-v2\app\build\outputs\apk\debug\app-debug.apk

Taille : 240 020 443 octets.
SHA-256 : EF0B13186306088AC172F39035C40A4DEEFA3161668DF4115F14F42B1E1E8F92.

Journaux principaux dans G:\DevServer\logs :
- tvflow-groq-final-safe-build.log
- tvflow-groq-proxy-tests.log
- tvflow-groq-final-emulator-5554-tests.log
- tvflow-groq-final-emulator-5556-tests.log
- tvflow-groq-ui-searches.json

## Reste à faire avant validation finale

1. Révoquer la clé partagée dans le chat ; saisir sa remplaçante dans la console
   masquée ouverte sur le PC. Ne pas la remettre dans le chat.
2. Confirmer que l'organisation Groq utilise le plan gratuit et aucune facturation.
   Aucun changement de facturation n'a été effectué par cette mission.
3. Exécuter start-groq.ps1 -Verify pour les appels réels limités par le budget,
   puis mesurer réponses, erreurs, jetons réels et latence.
4. Configurer et vérifier une route HTTPS privée pour les appareils physiques.
   Aucun port public, Funnel ni passerelle sans authentification n'a été créé.
   En l'état, l'application reste sur son moteur local par défaut.
5. Valider les recherches combinées et les similitudes sur des exemples présents
   et suffisamment documentés dans le catalogue.
6. Mesurer/optimiser les recherches locales lentes ; compléter les tests de
   navigation, épisodes, EPG, sous-titres et appareils physiques.
