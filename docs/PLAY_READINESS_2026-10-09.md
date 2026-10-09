# TVFlow V2 — Audit de prépublication (09/10/2026)

## Environnement
- PC Windows DevServer, compilation Gradle locale uniquement.
- Xiaomi Mi Box 3 sous Android 9, résolution 1920×1080, connexion ADB Wi-Fi.
- APK GitHub installé en mise à niveau sans suppression : TVFlow 1.0.86 (2020100081).
- Source de référence : branche `main` du dépôt privé `TvFlow-v2`.

## Vérifications effectives
| Test | Résultat | Limite |
| --- | --- | --- |
| Installation ADB `install -r` | PASS | Ne valide pas l'installeur graphique |
| Démarrage `MainActivity` et maintien du processus | PASS | Une Mi Box seulement |
| Premier rendu du fond vidéo | PASS (log `Background first frame`) | Un contenu seulement |
| 32 déplacements directionnels/retour | PASS, aucun crash ou ANR | Ne valide pas le clic de tous les boutons |
| 250 déplacements Monkey D-pad | PASS, 0 crash ou ANR | Pas de clic/toucher/test fonctionnel |
| Ouverture d'une fiche série, focus saison/épisodes, entrée et retour du lecteur | PASS navigation, aucun crash | La vidéo elle-même n'est pas confirmée |
| Lecture épisode MKV sur Mi Box | INCONCLUSIF | Codec AVC initialisé, capture ADB noire ; vérifier image/son sur téléviseur |
| Parsing Xtream `get_vod_info` avec `info` tableau | DÉFAUT CONSTATÉ, CORRIGÉ, tests unitaires PASS | Version corrigée 1.0.88 installée sur Mi Box |
| Fenêtre Nouveautés à la télécommande | DÉFAUT CONSTATÉ : OK non capturé par le contenu défilable | Correctif du focus/bouton central compilé, à retester sur APK suivant |
| Textes des nouveautés | DÉFAUT CONSTATÉ : sujets de commits anglais visibles | Résumés français concis intégrés, à retester sur APK suivant |
| 45 tests unitaires (7 suites) | PASS, 0 échec, 0 erreur | Inclut quatre réponses Xtream `info` atypiques |
| Gradle `bundlePlayRelease`, R8, lint vital | PASS | AAB non signé |
| Manifeste Play sans `REQUEST_INSTALL_PACKAGES` | PASS | Play Console doit encore examiner l'application |
| PSS pendant navigation | ~172 à ~315 Mo selon état | Pas une mesure de jank FPS |

## Non validé (bloque la certification de qualité)
- Lecture **réelle et prolongée** : Live, VOD/films, épisodes, Media3 et fallback VLC, audio/sous-titres, Cast, EPG. Un écran noir en capture ADB ne suffit pas à prouver une panne vidéo ou une lecture réussie.
- Chaque commande TV et téléphone : focus D-pad, double appui de prévisualisation Live, gestes tactiles, rotation, petits écrans, clavier/recherche.
- Trois ou davantage serveurs Xtream aux structures de catégories, dates, extensions, épisodes et flux différentes, y compris serveurs lents/erronés et grands catalogues.
- Reconnexion réseau, 4G/5G, Wi-Fi, reprise après mise en veille, app en arrière-plan, mémoire prolongée, ANR, batterie et rendu 60 FPS.
- Vérifications Google Play : clé de signature pérenne, informations publiques, politique de confidentialité, Data safety, droits sur les contenus et ressources visuelles, exigences Android TV.

## Publication
- Variante séparée `playRelease` ajoutée : pas de téléchargement d'APK externe ni permission d'installation, tout en conservant la distribution GitHub existante.
- App Bundle `app-playRelease.aab` construit, mais **non signé** avec une clé d'envoi : ne pas soumettre tel quel.
- Aucun envoi vers Play Console tant que les tests réels et obligations ne sont pas validés.

## Critère de sortie
- Aucun crash bloquant sur au moins Mi Box + téléphone et plusieurs profils de serveurs ; lecture VOD et Live confirmée, navigation et actions testées fonctionnellement.
- Bundle signé et correctement versionné ; politiques Google Play vérifiées ; test fermé Play Console avant diffusion large.