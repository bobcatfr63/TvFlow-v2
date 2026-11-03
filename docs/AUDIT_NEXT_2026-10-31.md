# TVFlow-v2 Next — audit technique et validation

31 octobre 2026 • audit initial et deux lots de corrections • PC Windows local

## Verdict

**La lecture Live fonctionne sur la Mi Box, mais l’application ne peut pas encore être déclarée prête pour une release.** Le parcours téléphone portrait est bloqué, la vidéo mobile avec libVLC présente des erreurs de décodage et manque de commandes, et plusieurs écrans conservent une présentation différente de l’accueil cinématographique.

L’audit a produit une compilation locale, des tests unitaires et instrumentés, des appels réels aux services configurés, des mesures et des captures ADB. Il ne constitue pas une certification exhaustive de tous les flux et appareils. Les contrôles non réalisés sont explicitement répertoriés ci-dessous. Aucun pourcentage de fidélité graphique n’est attribué.

## 1. Périmètre et identité vérifiés

| Élément | Résultat |
|---|---|
| PC | DESKTOP-8VD83BL, accessible par Desktop Commander |
| Worktree | `G:\DevServer\worktrees\TVFlow-v2-Next-cinematic` |
| Branche | `feature/tvflow-next-cinematic-ui` |
| HEAD initial | `860b54b` — conditional TMDB cinematic backdrops |
| Git avant intervention | Propre, aucune modification non commitée |
| Application installée et testée | `com.tvflow.app.next`, nom TVFlow-v2 Next |
| Original | `com.tvflow.app` : aucune installation, désinstallation ou modification de données effectuée |
| Compilation | Gradle sur le PC Windows, mode hors ligne avec dépendances en cache ; aucun runner cloud |
| Versions présentes avant audit | Émulateur : 1.0.212-next ; Mi Box : 1.0.217-next |
| Lots compilés pendant l’audit | 1.0.218-next puis 1.0.219-next, APK debug pour validation |
| Publication | Aucune release ni publication de production |
| Conservation | Pas de suppression de profil, base, historique ou favori existant ; pas de migration de schéma |

Les essais de lecture ajoutent normalement une entrée de progression/historique. Un favori de film ajouté durant l’essai a été retiré pour revenir à son état initial. Les essais de rafraîchissement et de gros catalogue utilisent une base Room en mémoire indépendante des données utilisateur.

### Appareils

| Environnement | Disponibilité et portée |
|---|---|
| Émulateur mobile x86_64 | Android 15/API 35, surface native 1080 × 2400, application affichée en paysage 2400 × 1080 |
| Xiaomi MIBOX3 | Android 9/API 28, captures 1920 × 1080, navigation DPAD et lecture réelle |
| Autre Android TV Box | Visible en ADB ; non utilisée pour éviter de mélanger les résultats |
| Galaxy A54 physique | Absent de la liste ADB au moment de l’inventaire ; non testé |

## 2. Architecture et dépendances

Application mono-module `:app`, avec dossiers `data`, `domain`, `di` et `ui`. Les ViewModels utilisent Hilt, StateFlow et `viewModelScope`. Room est la source locale du catalogue. Retrofit/OkHttp communiquent avec Xtream. Les grilles Films/Séries utilisent Paging 3. Media3 et libVLC coexistent ; sur mobile, le lecteur VOD privilégie libVLC.

Ce découpage est exploitable et ne justifie pas une réécriture globale. Il ne s’agit toutefois pas d’une Clean Architecture strictement séparée : `data/metadata/TmdbMetadataClient.kt` dépend de classes dans `ui/search`, et le dépôt central regroupe beaucoup de responsabilités.

| Composant | Version déclarée dans le worktree |
|---|---|
| Gradle / AGP / Compose compiler plugin | 9.6 / 9.4.0 / 2.4.20 |
| KSP / Hilt | 2.3.10 / 2.60.1 |
| SDK Android | min 24, compile 36, target 36 |
| JVM cible | 17 |
| Compose BOM / TV Material | 2025.08.01 / 1.1.0 |
| Room / schéma | 2.8.5 / 14 |
| Paging | 3.3.6 |
| Lifecycle / Navigation | 2.9.3 / 2.9.3 |
| WorkManager | 2.10.4 |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 |
| Media3 / libVLC | 1.11.1 / 3.7.6 |
| Coil | 3.3.0 |

Ces versions ont été lues dans les fichiers, puis compilées ensemble. Ce tableau ne prétend pas établir les dernières versions disponibles ni l’absence de vulnérabilité dans les dépendances transitives. Lint signale des mises à jour possibles ; aucune mise à niveau automatique n’a été faite pendant cet audit.

### Qualité et redondances

- `XtreamRepository.kt` : environ 2 183 lignes ; synchronisation, EPG, recherche, favoris, métadonnées, construction d’URL et diagnostics sont réunis.
- `DashboardScreen.kt` : environ 1 417 lignes ; `GuideScreen.kt` : 1 033 ; `PlayerScreen.kt` : 1 013 ; `SettingsScreen.kt` : 927 ; `MediaLibraryScreen.kt` : 712.
- `NextMobileDashboard` n’a pas de site d’appel dans le code de production recherché. Le mobile passe par une réduction graphique de `NextCinematicDashboard`.
- `StableFocusPreview` n’a pas de site d’appel de production trouvé. Il ne protégeait donc pas les aperçus réellement utilisés.
- Deux circuits de mise à jour subsistent : tableau de bord et overlay global, avec des gardes différentes pour Next ; un worker parse également le manifeste de son côté.
- Plusieurs utilitaires nettoient séparément les titres, et la détection TV n’est pas uniforme : helper `isTelevisionUi()` contre test direct de `FEATURE_LEANBACK` dans la bibliothèque.
- Les écrans collectent souvent avec `collectAsState()` plutôt qu’avec une collecte liée au cycle de vie. Ce point augmente le travail possible lorsque la composition reste présente en arrière-plan ; il ne prouve pas, à lui seul, une fuite mémoire.
- Aucun usage de `GlobalScope`, `runBlocking` ou `allowMainThreadQueries` n’a été trouvé dans la recherche ciblée des sources de production. Les scopes applicatifs de longue durée doivent rester réservés aux services applicatifs.

## 3. Backlog technique priorisé

Les statuts « confirmé » reposent sur le code ou une preuve d’exécution indiquée. Les risques ne sont pas présentés comme des crashs déjà observés. Les chemins sont relatifs au worktree vérifié.

| ID / priorité | Symptôme et cause racine | Fichiers concernés | Risque / correction recommandée | Test de validation / état |
|---|---|---|---|---|
| A01 — P1 | Aperçu dès le focus ; Home publiait immédiatement l’ID, Guide faisait de même dans `focusChannel` | `ui/home/HomeViewModel.kt`, `HomeScreen.kt`, `ui/guide/GuideViewModel.kt`, `GuideScreen.kt` | Connexions IPTV involontaires, comportement différent de la règle demandée. Centraliser sélection, second appui et délai de 2 s | Corrigé par `ui/common/ExplicitLivePreview.kt`. Trois tests unitaires passent ; captures DPAD Live sur Mi Box : focus et premier appui sans aperçu, deuxième avec aperçu, suivant en plein écran. Le chronométrage fin est couvert par le test, pas par une mesure vidéo image par image |
| A02 — P1 | Dates de sortie précises perdues au rafraîchissement : les copies Room conservaient plusieurs métadonnées mais omettaient `releaseDateIso` et `releaseDateSource` | `data/local/TVFlowDao.kt` : remplacements VOD, séries et catalogue combiné | Perte d’enrichissement et tri chronologique dégradé. Conserver les valeurs locales lorsque la réponse entrante ne les fournit pas | Deux tests instrumentés ont d’abord échoué avec date attendue / valeur null ; conservation ajoutée aux quatre copies. Résultats finaux dans le journal de validation |
| A03 — P1 | libVLC ignorait les événements pause/stop ; ses listeners retenaient les callbacks initiaux | `ui/common/VlcFallbackPlayer.kt` | Audio/vidéo pouvant continuer hors écran ; callbacks de progression obsolètes. Observer le Lifecycle et utiliser `rememberUpdatedState` pour les callbacks conservés | Correctif compilé et installé. Lecture VOD exercée ; reprise manuelle/automatique après appel téléphonique ou verrouillage non validée sur téléphone physique |
| A04 — P1 | Portrait inaccessible : Next impose `sensorLandscape` | `app/build.gradle.kts`, `MainActivity.kt` | Parcours mobile portrait impossible. Autoriser l’orientation mobile uniquement après adaptation des écrans ; maintenir la TV et le lecteur en paysage | Code et `dumpsys activity` confirment l’orientation ; demande de rotation portrait sur émulateur. Non corrigé : changer uniquement le manifeste exposerait les débordements existants |
| A05 — P1 | Accueil mobile réduit depuis une surface fixe de 1 400 × 620 dp via `graphicsLayer` | `ui/dashboard/NextCinematicDashboard.kt` | Textes et cibles tactiles trop petits, espace mal exploité en portrait. Construire des layouts selon largeur/hauteur disponibles ; réutiliser le contenu et les actions | Captures réelles paysage ; contrôle de cibles à 48 dp et fontScale 1,3/1,5 à ajouter au lot responsive |
| A06 — P1 | VOD mobile sans barre de progression, pause, recherche temporelle et sélection des pistes dans le chemin VLC | `ui/vod/VodPlayerScreen.kt`, `ui/common/VlcFallbackPlayer.kt` | Fonctionnalités de lecture importantes indisponibles. Introduire un contrôleur de lecture commun pilotant réellement le moteur actif | Capture après appui : informations et bouton TV seulement. Test futur : pause, ±10 s, seek, audio et sous-titres avec les deux moteurs |
| A07 — P1 | Erreurs réelles de décodage VLC sur l’émulateur : timeout d’entrée, deadlock évité, surface de sous-titres absente | Mêmes fichiers ; politique de choix du moteur | Lecture mobile instable. Comparer Media3 d’abord et VLC de secours sur plusieurs codecs ; éviter un changement global fondé sur le seul émulateur | Images décodées observées, mais stabilité non validée. Reproduire sur A54 avec même fichier et vérifier progression + frames + audio, puis second codec |
| A08 — P1 | Rafraîchissement Xtream charge des listes complètes VOD et séries en parallèle ; Room charge aussi les anciennes lignes complètes pour les fusionner | `data/repository/XtreamRepository.kt` vers lignes 490–555 ; `data/local/TVFlowDao.kt` | Pics mémoire avec DTO + nouvelles entités + anciennes entités + maps. Ingestion JSON en flux vers tables de staging, lots bornés et fusion SQL transactionnelle | Tests 100k passent mais ne prouvent pas un pic mémoire borné. Mesurer le pic pendant réception réseau, fusion et indexation ; préserver les tables actuelles en cas d’échec |
| A09 — P1 | Suggestions de série chargent toute la catégorie, puis trient avant `take(10)` | `ui/series/SeriesDetailsViewModel.kt`, `XtreamRepository.observeSeries`, DAO | Enfreint la contrainte de ne pas charger une grande catégorie pour construire un écran. Déplacer filtre, tri et LIMIT dans Room | Test avec catégorie 25k : dix résultats, ordre correct, nombre de lignes hydratées borné |
| A10 — P1 | Collision de port TCP avec l’original : `EADDRINUSE` réel, ports fixes identiques | `connect/TvFlowConnect.kt` | Synchronisation/transfert Next indisponible ou destinataire incorrect. Séparer et versionner la découverte Next, annoncer le port et migrer les pairs ensemble | Logs confirmés sur émulateur avec les deux applications installées. Non corrigé sans protocole de coexistence ; original laissé intact |
| A11 — P1 | Réception de commandes et favoris LAN sans authentification ni appairage dans `handleClient` ; seul le caractère privé de l’IP est vérifié | `connect/TvFlowConnect.kt`, `TVFlowApplication.kt` | Un autre client du LAN peut envoyer des commandes ; risque d’écrasement de favoris avec profil/timestamp acceptés. Appairage, secret par pair, messages signés, taille et débit bornés | Constat de code ; aucun message offensif envoyé. Tester rejet d’un pair inconnu, replay, mauvais profil et paquet surdimensionné dans un réseau isolé |
| A12 — P1 | TMDB ne renvoie aucune métadonnée et Groq aucune réponse sur Mi Box ; Groq également absent sur émulateur | `data/metadata/TmdbMetadataClient.kt`, `ui/search/AiGatewayConfig.kt`, `GroqIntentClient.kt`, réglages | Fonctions IA/cloud non validées pour ce profil d’appareil. Distinguer configuration manquante, session refusée, quota et réseau sans exposer de secret | TMDB Inception : résultat + panorama sur émulateur en ~1,70 s ; aucun résultat sur Mi Box en ~24 ms. Groq : aucune réponse en ~33/111 ms. Ces durées ne prouvent pas une panne fournisseur ; inspecter l’état de configuration |
| A13 — P2 | SQL de grille trie avec CASE et `COLLATE NOCASE` sans index composite adapté | `data/local/MediaEntities.kt`, `TVFlowDao.kt` | Tri temporaire sur gros catalogues. Créer des requêtes distinctes par tri, index composites profil/catégorie/tri et migration explicite | `EXPLAIN QUERY PLAN` réel : index categoryId + TEMP B-TREE FOR ORDER BY. Benchmark page initiale et suivante avant/après, sur base persistante |
| A14 — P2 | Paging utilise pageSize 60/prefetch 20 sans `maxSize` explicite | `XtreamRepository.pagedVod`, `pagedSeries` | Les pages visitées peuvent s’accumuler pendant une longue navigation. Fixer un budget de pages et vérifier le retour en arrière | Parcours de plusieurs milliers d’items, mémoire stable et position de scroll conservée ; pas de conclusion OOM issue de cette seule configuration |
| A15 — P2 | Recherche globale conserve toutes les clés normalisées en RAM, jusqu’à quatre profils | `XtreamRepository.searchGlobal`, `buildSearchCache` | Coût de première recherche et RAM proportionnels au catalogue ; objets complets hydratés seulement pour les meilleurs résultats, point positif | Mesurer première recherche puis cache sur 100k+ ; envisager colonne normalisée/index FTS en conservant accents et classement déterministe |
| A16 — P2 | Travail applicatif au démarrage, intro vidéo et préchargements concurrents ; démarrage froid lent | `ui/startup/StartupScreen.kt`, `StartupPreloader.kt`, `DashboardViewModel.kt`, `TVFlowApplication.kt` | Retard d’accès et frames sautées. Mesurer séparément première image, catalogue utilisable et intro ; rendre l’intro facultative, différer les tâches non visibles | Émulateur : am start froid 6,67 s puis 7,55 s selon build ; première relance après installation 16,08 s. Mi Box : une commande a expiré à ~10,19 s mais l’app s’est ensuite affichée. Ce timeout n’est pas une preuve d’ANR |
| A17 — P2 | Titre Favoris noir sur fond très sombre | `ui/favorites/FavoritesScreen.kt` | Lisibilité défaillante : Text sans couleur explicite hors Surface. Utiliser onBackground | Corrigé dans le second lot ; capture avant et validation finale du rendu |
| A18 — P2 | Accueil cinématographique, autres écrans visuellement hétérogènes ; en-tête absent de Films mobile | `ui/dashboard/*`, `ui/library/MediaLibraryScreen.kt`, `ui/home/*`, `ui/settings/*` | Expérience fragmentée. Mutualiser shell, couleurs, typographie, états et focus ; conserver des layouts propres au support | Captures et tableau UI ci-dessous ; pas de suppression de fonctionnalités pour obtenir une maquette |
| A19 — P2 | États temporaires présentés comme absence de chaînes TNT avant chargement ; état vide réapparaissant à l’ouverture | `HomeViewModel.kt`, `HomeScreen.kt` | Faux diagnostic de catalogue vide alors que les 25 chaînes apparaissent ensuite. Distinguer chargement, vide, erreur et données prêtes | Captures 13/25 : message vide puis TNT réelle. Réouvertures à froid et changement de profil avec base lente |
| A20 — P2 | Appels réseau synchrones OkHttp dans des contextes IO et certains `runCatching` génériques | Métadonnées, mises à jour, dépôt, ViewModels | IO ne bloque pas directement Main, mais l’annulation n’arrête pas forcément l’appel ; CancellationException parfois absorbée. Standardiser une requête cancellable et relancer l’annulation | Réseau ralenti, navigation arrière, disparition de l’écran ; vérifier l’annulation effective et les requêtes en vol. Groq possède déjà `invokeOnCancellation { call.cancel() }` |
| A21 — P2 | Clé TMDB en préférences DataStore en clair, contrairement aux identifiants Xtream chiffrés | `data/local/SettingsStore.kt`, `CredentialsStore.kt` | Secret accessible dans une extraction du stockage applicatif. Ajouter une migration vers chiffrement Keystore sans modifier l’API officielle SettingsStore | Test migration ancienne valeur, redémarrage, restauration et absence de secret en logs/backup non chiffré |
| A22 — P2 | Canal de publication par défaut et identité de build ne sont pas exclusivement Next | `.devserver.json`, `app/build.gradle.kts`, `VERSION`, `settings.gradle.kts` | Sans variable NEXT, le build produit l’identifiant original ; configuration release par défaut `tvflow-latest`. Créer une variante Next explicite et contrôler package/tag/nom avant publication | Pendant cet audit, variable NEXT et vérification de metadata APK avant installation. Aucun tag ni release modifié |
| A23 — P3 | Dette de lisibilité : gros fichiers, composants dormants, logique update et nettoyage de titres répétés | Fichiers mentionnés dans architecture | Maintenance plus risquée. Extraire progressivement à comportement constant, puis supprimer seulement les éléments réellement inutilisés | Inventaire des références, tests de navigation, screenshots avant/après ; pas de découpage massif sans besoin |

**P0 : aucun crash bloquant ni suppression de données utilisateur observé dans les parcours exécutés.** Cela n’établit pas l’absence de P0 dans les parcours non testés. La perte de dates a été reproduite dans une base synthétique, puis corrigée.

## 4. UI/UX — comparaison avec la cible

La cible exploitable ici est la description fournie : fond profond, navigation translucide, bleu électrique, focus lumineux, panorama TMDB, trois actions, raccourcis et collections compactes. Aucun fichier de référence graphique de conception n’est versionné parmi les images recherchées dans le worktree. Une capture historique a été identifiée, mais aucun comparatif pixel à pixel reproductible avec une maquette de référence n’a été établi pendant cet audit.

Les captures de début de session proviennent des APK déjà installées : mobile 1.0.212 et Mi Box 1.0.217. Les captures explicitement marquées Next218 et les scénarios Live proviennent du premier lot compilé, puis les dernières captures du lot final portent Next219. Une capture n’est pas une mesure du temps complet de chargement.

| Écran | Référence | État actuel observé | Écarts | Priorité |
|---|---|---|---|---|
| Accueil | Panorama + navigation haute + 3 actions | Fond sombre, bleu actif, panorama réel sur émulateur, raccourcis et rails ; affiche/ambiance de repli sur Mi Box | Mobile mis à l’échelle ; chaînes de titres parfois tronquées ; détails et lecture aboutissent à des parcours proches ; métadonnée HD générique | P1 mobile, P2 TV |
| Direct TV | Catégories, logos, EPG, aperçu contrôlé | Catégories et logos présents ; TNT retrouve 25 chaînes après chargement ; aperçu puis plein écran réels à la télécommande | Présentation noire utilitaire ; message vide trop précoce ; source sélectionnée et focus visuel doivent être mieux distingués | P1 comportement corrigé, P2 visuel |
| Guide TV | Grille horaire lisible et fluide | Grille, heures, catégories et état sans chaîne capturés sur mobile ; EPG actuel/suivant visible dans le lecteur Mi Box | Palette et rail latéral différents de l’accueil ; grille TNT réelle capturée sur Mi Box dans le lot final ; navigation détaillée à compléter | P2 |
| Films | Panorama + filtres + affiches compactes | Catégories, tri, recherche et grille paginée ; affiches présentes après chargement ; fiche avec lecture et favoris | Sur mobile pas de bandeau panorama ; seulement trois grosses cartes visibles ; titres fournisseurs longs et synopsis anglais possibles | P1/P2 |
| Séries | Panorama, collections, saisons et épisodes | Grille, fiche, saison 1 et plusieurs épisodes capturés ; réponse API des épisodes analysable | Présentation longue en paysage ; synopsis occupe presque l’écran ; collection espagnole affichée par défaut selon la catégorie existante | P2 |
| Recherche IA | Saisie, résultats, statuts explicites | Champ, microphone, suggestions et compteur local visibles | Groq non opérationnel dans les essais ; la recherche locale ne doit pas être confondue avec un appel IA réussi | P1 |
| Recherche standard | Résultats bornés et clavier utilisable | Parcours présent dans le code et entrée de recherche bibliothèque visible | Recherche interactive avec clavier et résultats non validée intégralement pendant cette session | À tester |
| Favoris | Liste lisible, persistance et gestion | Onglets Chaînes/Films/Séries, état vide ; ajout et retrait film exercés | Titre noir corrigé ; liste sans affiches premium ; synchronisation LAN à sécuriser ; persistance multi-appareils non certifiée | P1 sync, P2 UI |
| Réglages | Groupes cohérents et accessibles | Page racine groupée capturée | Titre générique TVFlow ; grandes lignes utilitaires ; toutes les sous-pages n’ont pas une capture et un contrôle DPAD | P2 |
| Fiche film | Détails, favorite et lecture | Affiche, résumé, note, date, actions et suggestions capturés | Affiche agrandie en arrière-plan plutôt que vrai panorama vérifié ; note trop précise, noms fournisseurs ; hiérarchie à harmoniser | P2 |
| Fiche série | Saisons/épisodes accessibles | Liste réelle de saisons/épisodes, vignettes et bouton Lire | Actions d’épisode sous le pli, synopsis long ; reprise et épisode suivant non exécutés | P2 |
| Lecteur Live | Plein écran + infos lisibles | Image réelle, EPG courant/suivant et barre programme sur Mi Box | Flux nommé 4K en réalité 1080p ; qualité à afficher depuis le format décodé | P2 |
| Lecteur VOD mobile | Contrôles tactiles complets | Images réelles après chargement, overlay titre/format/TV | Manque de commandes VLC ; erreurs de décodage ; pas de validation de lecture longue | P1 |
| Connexion/onboarding | Parcours TV et tactile utilisable | Code présent ; profils existants conservés | Nouvelle connexion, QR/appairage et premier lancement vierge non testés pour ne pas remplacer le profil utilisé | À tester sur profil isolé |
| Mises à jour/dialogues | Popup FR, erreur, intégrité, installation | Chemins de code et vérification de l’APK inspectés | Pas de nouvelle release contrôlée disponible pour un test complet ; pas de capture de popup de mise à jour réelle | À tester |

### Composants Compose proposés

Conserver les ViewModels et les routes existantes dans un premier temps. Créer un petit système commun :

1. `NextScreenScaffold` : fond, insets, chargement/erreur/vide, variantes téléphone et TV.
2. `NextTopNavigation` : onglet actif séparé du focus DPAD ; version compacte tactile.
3. `CinematicHero` : modèle de contenu commun, panorama vérifié et repli explicite, titre, métadonnées et actions réelles.
4. `MediaPosterCard` et `MediaRail` : taille par support, ratio poster stable, image de repli, sémantique/accessibilité, clés stables.
5. `CategorySelector` : liste latérale sur TV, sélecteur compact sur mobile, état sélectionné et focus distincts.
6. `NextActionButton` et `FocusSurface` : cible tactile minimale 48 dp, focus contrasté sans redimensionnement du texte.
7. `PlaybackControls` piloté par une interface de moteur : pause, seek, pistes, état buffer, durée/progression, sans boutons qui pilotent l’autre lecteur.

Les composables reçoivent des données bornées et des callbacks. Ils ne doivent pas déclencher eux-mêmes un chargement global du catalogue. Éviter de multiplier des copies Home/TV/Mobile avec sélection de média et favoris divergents.

## 5. Matrice de compatibilité

| Contrôle | Téléphone portrait | Téléphone paysage — émulateur | Android TV — Mi Box |
|---|---|---|---|
| Orientation | Bloquée par l’application | Oui, captures réelles | Oui, 1080p |
| Accueil | Non utilisable en portrait | Accessible, mise à l’échelle à corriger | Accessible, DPAD et focus visibles |
| Catalogue Films/Séries | Non validé | Grilles, fiches, saisons et épisodes affichés | Grille séries observée avant lot ; catalogue et API vérifiés ; régression visuelle finale partielle |
| Live | Non validé | Écran Direct capturé ; lecture mobile Live non exécutée | Validé sur un flux réel en 1080p, >90 s de progression |
| Deuxième appui / aperçu | Non validé | Tests unitaires du contrôleur | Validé visuellement à la télécommande dans Direct ; activation dans la grille EPG à rejouer |
| EPG | Non validé | Guide capturé, parcours complet non validé | API lisible + grille TNT capturée + programme courant/suivant dans le lecteur |
| VOD | Non validé | Images décodées mais stabilité/contrôles insuffisants | Lecture VOD non exécutée pendant cet audit |
| Épisodes / suivant / reprise | Non validé | Liste affichée ; lecture et enchaînement non exécutés | Réponse API parsée ; lecture et enchaînement non exécutés |
| Audio / sous-titres / seek | Non validé | Commandes VLC absentes dans le parcours inspecté | Sélection de pistes et seek non testés |
| Retour Android | Non validé | Navigation exercée entre plusieurs écrans | Dashboard → Direct → lecteur → retour exercé |
| Rotation et clavier | Échec orientation ; clavier portrait non testable | Clavier et rotation depuis lecteur non certifiés | Sans objet pour clavier tactile ; saisie TV à tester |
| 720p / 4K réels | Non testé | Non testé | Seulement 1920 × 1080 confirmé ; aucun succès 4K déduit du nom de chaîne |
| Version Android physique | Non testé | Émulation API 35, pas un test A54 | Android 9 physique |

## 6. Performances mesurées

Mesures ponctuelles, builds debug, réseau local réel et appareils non dédiés à un benchmark. Elles ne sont pas des médianes, des p95, ni un profil de production minifié.

| Mesure | Émulateur | Mi Box |
|---|---|---|
| Cache Live / VOD / séries | 21 821 / 107 797 / 25 260 lignes | 22 019 / 107 829 / 25 279 lignes |
| Lignes EPG en cache | 16 935 | 17 198 |
| Catégories API Live / VOD / séries | 360 / 143 / 97 | 360 / 143 / 97 |
| Réponse catégories | ~407–546 ms par appel | ~424–543 ms par appel |
| Informations épisodes — échantillon | ~813 ms, une saison | ~1 169 ms, une saison |
| Test synthétique 100k : insertion, lot 1 | 7 406 ms | 27 979 ms |
| Test synthétique 100k : remplacement, lot 1 | 7 973 ms | 38 334 ms |
| Temps des 4 tests synthétiques, lot 1 | 16,20 s | 74,82 s |
| Temps des 4 tests, lot final | 18,90 s sur émulateur | Voir validation finale |
| Mémoire ponctuelle application | PSS ~206–269 MiB selon écran/instant | PSS ~113–119 MiB selon instant |
| Heap JVM affiché à la fin du stress, lot 1 | 27 MiB | 27 MiB |
| Heap natif alloué à la fin du stress, lot 1 | 24 MiB | 22 MiB |
| Première activité froide — exemples | 6,67 s ; 7,55 s sur lot final ; 16,08 s après première installation | Un timeout de la commande am start à ~10,19 s ; affichage ultérieur confirmé |

**Limites essentielles :** les heaps sont des instantanés en fin de test, pas des maxima. Le test 100k ne couvre pas le pic de désérialisation réseau Xtream et ne prouve pas l’absence d’OOM. Aucun pic CPU, nombre de recompositions, p95 de frame time, coût exact de chaque onglet ou série de démarrages à froid n’a été mesuré. Des messages « Skipped frames » ont été observés ; ils motivent une trace Perfetto/JankStats, pas une estimation inventée de FPS.

Le plan SQL réellement obtenu utilise l’index simple `categoryId` puis une B-tree temporaire de tri. Les grilles sont bien paginées ; les chemins de synchronisation et de suggestions ne le sont pas entièrement. Il faut traiter ces chemins avant d’affirmer que tous les usages restent bornés à 100k médias.

## 7. IPTV, TMDB et Groq : résultats réels

### IPTV / Live

Les catégories Xtream et les réponses EPG/épisodes ont été reçues et analysées sur les deux appareils. Les comptes et URL n’ont pas été exportés dans ce rapport. Les nombres en cache ne constituent pas une mesure d’un rafraîchissement complet effectué pendant l’audit.

Sur Mi Box, la séquence DPAD a été capturée : focus, premier appui sans aperçu, deuxième appui avec aperçu, troisième appui en plein écran. Le lecteur Media3 a journalisé une première image rendue puis plusieurs points de contrôle avec `playing=true`, `state=3`, `firstFrame=true`, format **1920x1080**, et une position progressant d’environ 6,36 s à 97,63 s. L’EPG « JT 13h » et le programme suivant apparaissent dans l’overlay.

Cette preuve porte sur un flux, une session et Media3 sur Mi Box. Elle ne valide pas toutes les chaînes, tous les formats, ni l’absence d’interruption pendant plusieurs heures. L’écoute audio humaine et la sélection de pistes n’ont pas été réalisées.

### VOD mobile / libVLC

La fiche film et son bouton Lecture ont été utilisés. Plusieurs images d’introduction différentes sont visibles dans les captures, ce qui prouve un début de décodage vidéo. Des erreurs libVLC apparaissent ensuite : `dequeue_in timeout`, `buffer deadlock prevented`, problème de surface de sous-titres et `NdkMediaCodec` erreur -13. Le contexte d’émulation peut contribuer à ces erreurs ; la cause codec/surface doit être comparée sur le téléphone physique.

Ne pas qualifier cette session de lecture VOD stable. Le chemin VLC n’expose pas les contrôles demandés et aucune avance/reprise/sélection audio complète n’a été validée.

### Métadonnées / IA

- Émulateur : recherche TMDB réelle d’Inception (2010), métadonnée correspondante et panorama présents, ~1,70 s.
- Mi Box : recherche TMDB sans résultat exploitable, ~24 ms ; absence de configuration/session possible, aucune conclusion de panne distante.
- Groq : aucune réponse exploitable sur les deux appareils. Les retours rapides peuvent indiquer une configuration absente/rejetée avant appel réseau. Recherche locale et réponse Groq doivent rester distinguées.
- Le client TMDB possède des caches RAM bornés, un single-flight par identité et un cooldown des échecs. Le client Groq possède un cache borné et une annulation de requête explicite. Ce sont des protections utiles.
- Cache hors ligne durable des panoramas, exactitude du compteur/quota distant, refus par appareil et changement de langue n’ont pas été certifiés ici. Aucun nouveau service payant n’a été ajouté.

## 8. Sécurité, données et mises à jour

Points positifs inspectés : identifiants Xtream chiffrés avec Android Keystore/AES-GCM, backup Android désactivé, FileProvider non exporté et propre au package, migrations Room explicites jusqu’au schéma 14, transactions de remplacement, conservation du cache quand la réponse catalogue est vide, contrôles de téléchargement APK (taille, SHA-256, package, version et empreinte du signataire installé).

Points à traiter : authentification des échanges LAN, coexistence Next/original sur les ports, clé TMDB en clair dans le stockage privé, journaux d’exception susceptibles de porter une URL dans certains chemins, transport HTTP globalement permis. L’HTTP peut être nécessaire au fournisseur IPTV : ne pas le désactiver aveuglément. Maintenir HTTPS pour les secrets cloud et réduire les exceptions réseau quand le contrat des fournisseurs le permet.

Le client update de Next vise un manifeste distinct `tvflow-next-latest`. Le téléchargement refuse un hôte initial autre que GitHub et vérifie l’APK avant installation. Les permissions d’installation et de notifications sont prévues. Ces vérifications ont été inspectées dans le code, pas validées par une mise à jour réelle publiée pendant l’audit. Le chemin d’installation après autorisation « sources inconnues », les échecs réseau, la signature incompatible et la préservation des données sur une mise à jour de release restent à tester avec deux APK contrôlées.

Les sources de notes de version et les libellés restent génériques TVFlow à plusieurs endroits. `.devserver.json` conserve un canal original par défaut : ajouter un garde de publication Next avant toute automatisation. Aucun secret n’a été imprimé ou ajouté aux tests ; les nouveaux diagnostics n’écrivent que des compteurs, durées, classes d’erreur et plans SQL.

## 9. Lots réalisés et validations

### Lot 1 — aperçus et cycle de vie

- Ajout d’un contrôleur commun `ExplicitLivePreview` : focus sans lecture, premier appui d’armement, deuxième appui avec attente 2 s, plein écran après aperçu actif, annulation lors du changement de chaîne/sortie d’écran.
- Raccordement des écrans Direct et Guide ; nettoyage du preview au stop et à la sortie de composition.
- libVLC : pause liée au Lifecycle et callbacks conservés actualisés.
- Compilation, APK et Lint locaux réussis : 1 min 22 s ; 148 tests unitaires, zéro échec.
- Installation avec remplacement de Next sur les deux appareils ; quatre tests de catalogue/préférences passent sur chacun ; services réels diagnostiqués séparément.
- Live DPAD et vidéo réelle validés sur Mi Box. Le parcours du Guide complet et les interruptions libVLC sur téléphone physique restent à compléter.

### Lot 2 — conservation des dates et contraste

- Régression reproduite : deux assertions de dates ont échoué sur le lot 1 (`expected date`, valeur `null`).
- Conservation de `releaseDateIso` et `releaseDateSource` ajoutée aux quatre fusions Room, sans changement de schéma.
- Couleur du titre Favoris explicitée avec `onBackground`.
- Compilation, APK et Lint locaux réussis : 1 min 10 s ; vérification `git diff --check` sans erreur.
- Build final de validation : **1.0.219-next**, versionCode **2020102219**, applicationId **com.tvflow.app.next**. Il s’agit d’un build debug installé pour les tests, pas d’une release de production.

### Registre de validation

| Vérification | Résultat et portée |
|---|---|
| Inventaire dépôt/branche/Git | Réussi, avant modifications |
| Compilation initiale + unitaires + Lint | Réussis, 3 min 29 s ; 145 tests ; zéro erreur Lint, 54 avertissements et 7 hints |
| Lot 1 | Compilation/Lint réussis ; 148 tests unitaires passent |
| Diagnostics services — émulateur et Mi Box | Collecte instrumentée exécutée sur chacun ; les services ont les résultats distincts décrits plus haut. « OK (1 test) » signifie collecte terminée, pas tous les services opérationnels |
| 100k + transactions + préférences, lot 1 | 4 tests passent sur chaque appareil, bases isolées |
| Régression dates avant correction | 2 échecs attendus et reproduits |
| Lot final : compilation / unitaires / Lint | Réussis ; résultats unitaires et warnings finaux vérifiés dans les fichiers produits |
| Lot final : transactions/dates/100k/préférences | Voir complément final de validation ajouté après la fin des sessions instrumentées |
| Live 1080p Mi Box | Réussi sur un flux, images + première frame + progression >90 s |
| VOD mobile | Début de décodage observé, stabilité et contrôles non validés ; erreurs consignées |
| Régression complète de tous les écrans après lot 2 | Non réalisée : captures ciblées, pas une campagne exhaustive |
| Release minifiée / installation production / rollback | Non réalisés ; aucune publication |

## 10. Plan d’action par lots indépendants

1. **Lecture et sécurité P1** : unifier les commandes Media3/VLC, reproduire le blocage codec sur A54, valider pause/reprise/cast sans lecteur fantôme ; isoler et authentifier le protocole Next LAN. Gates : frame réelle, progression, audio, seek, pistes, sous-titres, background et sortie.
2. **Données et mémoire P1** : ingestion Xtream en flux/staging, fusion SQL préservant métadonnées et préférences, suggestions bornées en SQL. Garder la base actuelle jusqu’au commit transactionnel. Gates : 100k/25k, réponse partielle/vide, annulation, manque d’espace, deux profils.
3. **Responsive mobile P1** : layouts portrait/paysage réels, navigation compacte, insets, clavier, cibles 48 dp. N’ouvrir le portrait qu’avec Direct, Guide, bibliothèque, fiches, recherche, réglages et retour lecteur adaptés.
4. **Métadonnées et IA P1** : diagnostiquer l’état de configuration de chaque appareil via SettingsStore et le gateway existants ; vérifier quota/auth/cache et messages utilisateurs. Ne pas recopier un secret dans un rapport ou un argument de ligne de commande.
5. **Cohérence visuelle P2** : introduire les composants communs proposés, écran par écran. Garder pagination, actions et lecture réelles ; ajouter une comparaison reproductible avec le fichier de référence exact.
6. **Performance P2** : index/migration et requêtes par tri, budget Paging, démarrage sans vidéo obligatoire, mesures Perfetto et longue navigation Mi Box. Pas de promesse de FPS sans trace.
7. **Distribution** : variante Next explicite, canal et nom APK protégés ; campagne update avec APK signées contrôlées ; validation release minifiée sur TV + téléphone avant publication.

### Modifications à éviter

Ne pas réécrire le dépôt ou la navigation entière ; ne pas remplacer Room par des listes RAM ; ne pas supprimer libVLC avant d’avoir mesuré les codecs nécessitant le secours ; ne pas migrer destructivement la base ; ne pas fusionner les données de l’original avec Next ; ne pas appliquer en masse les mises à jour de dépendances pendant les corrections fonctionnelles.

## 11. Preuves et limites restantes

Les originaux PNG et les journaux de travail sont conservés sur le PC dans `G:\DevServer\audits\TVFlow-Next-20261031`. Le rapport HTML rassemble des copies JPEG réduites des captures réelles ; elles sont destinées à l’inspection visuelle, pas à une comparaison pixel parfaite. Les noms historiques de capture ne font pas foi : la capture nommée `33-guide-mibox` montre en réalité le Direct, et ne sert pas de preuve de Guide TV.

Reste à exécuter : parcours vierge et ajout de profil isolé, recherche standard et clavier, toutes les sous-pages de réglages, erreurs réseau forcées et reprise, lecture des épisodes et suivant automatique, reprise VOD après redémarrage, sélections de pistes et sous-titres, verrouillage/appel téléphonique, multi-appareils favoris authentifiés, tests longs de décodage, 720p/2160p réellement identifiés, accessibilité TalkBack/fontScale et mise à jour de release de bout en bout.

Ces contrôles sont laissés visibles dans le backlog : ni la compilation, ni Lint, ni un écran non noir ne remplacent leur exécution.

### Complément final — lot 2 effectivement terminé

- 1.0.219-next installée avec succès sur les deux appareils, sans désinstallation.
- 148 tests unitaires, zéro échec ; Lint : zéro erreur, 54 avertissements et 7 hints.
- Les quatre tests instrumentés de catalogue/transactions/préférences passent sur l’émulateur en 18,902 s et sur la Mi Box en 74,392 s. Les deux assertions de dates qui échouaient avant le correctif passent maintenant.
- Après installation finale, l’émulateur affiche l’application (am start : 7,545 s). La Mi Box a de nouveau expiré dans am start après 10,175 s, puis l’interface a été capturée : ce problème de démarrage reste ouvert.
- La demande de portrait laisse l’application en paysage, confirmée par la capture 34 et `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`. Le réglage de rotation libre initial de l’émulateur a été restauré.
- La capture 35 confirme le titre Favoris désormais blanc et lisible. La capture 36 montre l’ouverture du Guide ; la capture 37, prise après sélection TNT, confirme ensuite la grille réelle avec plusieurs chaînes, programmes et alignements horaires. Elle ne valide pas toutes les interactions de la grille. Le libellé « OK : aperçu » doit encore être aligné avec la règle de double appui.
- Les versions originales installées sont toujours 1.0.156 sur l’émulateur (dernière mise à jour le 29 octobre) et 1.0.164 sur la Mi Box (dernière mise à jour le 29 octobre). Elles n’ont pas été réinstallées pendant cette intervention.
- Les modifications restent dans le worktree de la branche demandée, sans commit/push ni release automatique. Le diff suivi est exporté sur le PC ; les trois nouveaux fichiers de contrôleur/tests sont également présents dans le worktree.

- Dernier contrôle du Guide : la sélection d’une cellule EPG annule désormais aussi un aperçu différé appartenant à une autre chaîne. Une nouvelle compilation et les contrôles associés sont consignés dans `final-guide-build.log`.

- Compilation du dernier garde EPG : réussie en 58 s ; 148 tests unitaires toujours sans échec. SHA-256 de l’APK debug finale : `C4ADC06F05148584AEFB42F02D4BD2FE628C11DF86EE71C7C0315C180ADE2994`.

- Après réinstallation de ce dernier APK : installation réussie sur les deux appareils ; lancement froid mesuré à 17,758 s sur émulateur, nouveau timeout am start à 10,393 s sur Mi Box. Les mesures varient fortement ; le démarrage doit être profilé et reste une anomalie ouverte.


## 12. Suite de l’audit — lecteur VOD, builds 1.0.220 à 1.0.224-next

Les essais emploient le profil IPTV déjà présent et de vrais films de son catalogue. Aucun compte de démonstration n’a été ajouté. Les flux sont testés successivement pour éviter des lectures concurrentes sur le même abonnement. Les identifiants et URL authentifiées ne figurent pas dans ce rapport.

### Corrections ciblées et preuves

| Anomalie | Cause racine et fichiers | Correction | Validation et limite |
|---|---|---|---|
| A06 — P1, commandes VOD mobile absentes | Le chemin téléphone utilise VLC alors que les contrôles natifs appartiennent à Media3. `ui/vod/VodPlayerScreen.kt`, `ui/common/VlcFallbackPlayer.kt` | Nouveau `VlcPlaybackController.kt` pilotant le moteur actif et `VlcVodControls.kt` : pause/reprise, ±10 s, progression, audio, sous-titres et callbacks d’épisodes | Build 223 : neuf contrôles automatisés sur flux réel réussis. Pause immobile, seek, dialogue pistes et reprise avec progression et nouvelle image. Les sous-titres disponibles sur le fichier testé se limitent à « Désactivés » ; aucun rendu de sous-titres ni changement de langue audible certifié |
| A03 — P1, cycle de vie VLC | Listener et callbacks conservés, lecture pouvant être demandée après passage en arrière-plan | Contrôleur lié au Lifecycle, intention de pause conservée, commandes rejetées après détachement, listener supprimé avant libération native | Neuf tests unitaires du contrôleur ; passage Home/retour pendant pause exécuté sur émulateur. Appel téléphonique et verrouillage sur A54 non testés |
| Reprise et seek imprécis | Reprise après délai fixe de 400 ms, seek VLC avec mode rapide et utilisation possible de la position du lecteur Media3 inactif | Attendre la disponibilité du seek, utiliser le seek précis de l’API VLC embarquée, lire/pause du moteur actif ; position zéro valide pour transfert | Seek réel et reprise exercés sur émulateur. Cast et transfert TV corrigés dans le code mais transfert réseau de bout en bout non exécuté |
| Commandes visuellement découpées sur émulateur | Superposition de la vue vidéo native et du contenu Compose ; corruption observée après démarrage vidéo | TextureView sur mobile, surface TV conservée, plan tactile dédié et composition Offscreen du panneau | La capture 65 du build 224 affiche titre, durée, slider et boutons complets. La validation finale avec image vidéo est distinguée ci-dessous ; pas de généralisation à tous les GPU |
| A07 — P1, décodage mobile | Erreurs natives initiales observées sur l’émulateur x86_64 ; la réussite des commandes n’établit pas la stabilité des codecs | Attachement de la surface sous-titres activé ; aucune refonte globale de la politique de moteur | Images et progression réelles observées sur les builds précédents, notamment 154 375 → 225 516 ms. Problème codec maintenu ouvert : A54, plusieurs formats et lecture longue à tester |

### Construction locale

APK debug **1.0.224-next**, versionCode **2020102224**, applicationId **com.tvflow.app.next**. Compilation, tests et lint exécutés sur le PC Windows local, hors ligne ; aucun runner cloud. Installation avec conservation des données sur l’émulateur et la Mi Box. **157 tests unitaires, zéro échec ; lint : zéro erreur, 54 avertissements et 7 indications.** Les quatre tests instrumentés par appareil du lot précédent n’ont pas été rejoués pour cette seule modification du lecteur.

SHA-256 de l’APK : `DDB8A53C49BD40137C3FA075E4216C48BC3AD6D961BE3D891B53CAF09B14A49C`.

Les neuf nouveaux tests couvrent le moteur lié, les limites de seek, la reprise différée, le chargement en arrière-plan, la pause manuelle, l’intention pendant buffering, les pistes, le détachement et les libellés de temps. Ils ne remplacent pas les essais natifs.

### Points maintenus ouverts et ordre du prochain lot

1. Finir les essais physiques du lecteur : A54, codecs, sous-titres réellement présents, épisodes précédent/suivant, cast et transfert TV ; conserver A07 ouvert.
2. Isoler et authentifier le protocole LAN de Next pour éviter collisions avec TVFlow original et commandes non appairées (A10/A11).
3. Borner les chargements catalogue et suggestions avant tout travail cosmétique étendu (A08/A09), puis budget Paging et index SQL.
4. Construire les layouts portrait et paysage avec des dimensions réelles ; le portrait reste bloqué (A04/A05).
5. Uniformiser les écrans avec les composants proposés et résoudre les états vides/chargement ; vérifier les configurations TMDB/Groq et le parcours de mise à jour.

Les modifications restent locales, non commitées et non publiées. TVFlow original reste intact. Les essais ajoutent normalement des positions de lecture à l’historique du profil existant.

### Répétition du scénario 224 : échecs conservés et contrôles complémentaires

Le premier lancement du script a précédé la disponibilité de la navigation (« Films » absent). La répétition a trouvé le catalogue. Un contrôle +10 s a échoué en comparant le journal initial à 0 avec la position restaurée d’environ 13 min 26 s ; l’interface montrait déjà cette dernière position. Après stabilisation, répétition native : **806 885 → 816 875 ms**, soit +9 990 ms, réussie. Le recul a donné **816 895 → 806 895 ms**. Le slider a déplacé la lecture de **816 875 → 691 156 ms**.

Le script copié par PowerShell avait mal décodé « Désactivés » : assertion sous-titres fausse puis arrêt sur libellé introuvable. Le dialogue réel montrait pourtant « ✓ Désactivés ». La suite a été reprise avec un script UTF-8 écrit directement ; les résultats initiaux restent dans les journaux et ne sont pas reclassés comme succès. Le test précédent du build 223 n’avait pas ce défaut de script.

Les quatre contrôles de reprise du build 224 ont ensuite réussi : +10 s répété, slider, pause conservée en arrière-plan à **691 156 ms**, puis reprise réelle jusqu’à **706 243 ms**. La capture 70 montre une image vidéo et l’ensemble du panneau lisible pendant la lecture ; le découpage des commandes n’y est plus observé. Le début noir de la capture 65 n’est pas utilisé comme preuve de lecture.

### Mi Box : régression catalogue vérifiée, lecture VOD non certifiée

Le build 224 ouvre l’accueil, Films et la fiche « Diversa (2026) » avec navigation DPAD. Un appui Entrée depuis le bouton Lecture lance Media3 : READY, durée **4 810 624 ms**, progression **11 832 → 26 857 ms** et playing=true dans les logs. **Les deux captures restent noires avec un fragment de texte en haut. Ce résultat n’est pas une lecture réussie.** Le retour Android a été envoyé pour arrêter le flux.

| Anomalie complémentaire | Cause | Fichiers à inspecter | Risque / correction recommandée | Validation requise |
|---|---|---|---|---|
| A24 — P1 : VOD Mi Box sans image exploitable dans les captures 75/76 | Non établie : rendu vidéo, superposition native/Compose ou limitation de capture à distinguer ; les logs READY et progression ne suffisent pas | `ui/vod/VodPlayerScreen.kt`, vue Media3/PlayerView, surface de rendu et logs codec | Lecture visuelle non certifiée ; relever première frame, dimensions décodées, erreurs codec et comparer l’écran TV physique à la capture avant de modifier le moteur | Même fichier puis second codec, deux images distinctes visibles et progression, commandes/focus DPAD, pause et retour ; comparer avec APK antérieur avant de qualifier une régression |

Matrice actualisée : mobile portrait non testable ; paysage émulateur avec commandes VLC, seek et reprise exécutés ; Mi Box avec catalogue/fiches DPAD exécutés et VOD tentée mais validation visuelle échouée. Le Live 1080p reste une preuve du lot précédent, pas un nouveau test du build 224. Aucune validation de production ne découle de ce lot.


## 13. Suite du 1er novembre 2026 — fluidité, lecture et grilles

Le worktree et la branche ont été revérifiés avant toute intervention. HEAD reste `860b54b`, branche `feature/tvflow-next-cinematic-ui`. Les modifications du 31 octobre ont été conservées. Lots locaux successifs à partir du build 225, sans publication, changement de schéma ni nouveau service.

### A09 : suggestions de séries bornées dans Room

Symptôme : ouvrir une fiche pouvait hydrater toute une catégorie puis trier en Kotlin pour conserver dix suggestions. Cause : `SeriesDetailsViewModel` utilisait `observeSeries(categoryId).first()`, puis filtre/tri/take. Correction : nouvelle requête suspendue `TVFlowDao.getSeriesSuggestions`, filtrée par profil/catégorie, exclusion de la série courante et `LIMIT 10` ; le repository ne convertit que les résultats. Tri préservé par année, mois puis nom, avec identifiant en dernier critère déterministe. Aucun changement de schéma.

Deux tests instrumentés réussissent sur chaque appareil après correction du runner : catégorie synthétique de 25 000 séries, ordre attendu, exclusions, isolation de profil et catégorie, résultat vide et répétabilité. La base est en mémoire et indépendante du profil utilisateur. Mesure ponctuelle de la requête : **Mi Box 158 ms, émulateur 3 ms**, dix entités retournées. Ce résultat ne constitue pas un benchmark réseau et ne prouve pas que l’import global Xtream est borné : A08 reste ouvert.

Le premier lancement JUnit a échoué à l’initialisation car la fonction de test inférait le retour entier de `Log.i`. Le retour a été déclaré explicitement `Unit`, puis les tests ont été reconstruits et rejoués. Durée des deux tests : Mi Box **10,541 s**, émulateur **3,819 s**. Les échecs initiaux restent dans les journaux.

### A24 : correction ciblée du rendu VOD TV

Le build 224 a été réessayé avant modification : écran noir reproduit sur « Diversa », avec progression Media3 dans les logs. Le Direct utilise déjà le layout `tvflow_texture_player` sur TV, tandis que la VOD créait un PlayerView avec SurfaceView par défaut. La VOD TV utilise désormais le même layout TextureView, tout en activant ses contrôles. Le chemin VLC mobile est conservé. Un callback `onRenderedFirstFrame` journalise les dimensions sans URL authentifiée. La validation visuelle après correction est détaillée ci-dessous ; READY seul n’est pas une preuve de lecture.

### Grilles Films et Séries sur téléphone

La grille et la recherche imposaient trois colonnes, quelle que soit la largeur. Les affiches avaient une hauteur fixe de 142 dp et une largeur variable : cadrage très large sur paysage. `MediaLibraryScreen.kt` utilise désormais `GridCells.Adaptive(112.dp)` sur mobile et un ratio d’affiche 2:3. Les cinq colonnes et les dimensions TV sont conservées. Les données restent paginées et les actions existantes sont conservées. Le portrait demeure verrouillé et n’est pas déclaré traité par ce lot.

### A25 — blocage au démarrage observé, cause exacte non établie

Sur le build 224 avant installation du lot, une capture réelle de l’émulateur présente « TVFlow-v2 Next isn't responding ». L’événement système porte « No response to onStartJob ». La trace du processus Next a été récupérée : le thread principal est Runnable dans la première composition de `TVFlowTheme` vers Theme.kt:164. Cette photographie ne démontre ni deadlock du thème ni blocage réseau sur ce thread. L’essai avait lieu pendant une compilation locale ; une causalité avec la charge hôte n’est pas établie.

Un nouveau démarrage du même build a abouti : `am start -W`, COLD, **11 428 ms**. Le problème reste **P1 ouvert**, et ne doit pas être masqué par le succès d’une relance. Prochain contrôle : série de démarrages à froid sans compilation simultanée, séparation initialisation application/WorkManager/première composition et acquisition de plusieurs stacks si réapparition. Aucune réécriture du thème fondée sur cette seule trace.

### Construction et conservation

Build final **1.0.229-next**, package `com.tvflow.app.next`, installé avec `adb install -r` sur les deux appareils. Compilation locale, tests unitaires **157 réussis**, zéro erreur lint, **43 avertissements et 7 indications** dans le rapport de ce build. Tests instrumentés ciblés exécutés sur le build 226 : deux réussis sur chacun des deux appareils. La requête Room est inchangée dans les builds 227 à 229 ; ces tests instrumentés n’y ont pas été relancés. Les autres tests instrumentés du 31 octobre n’ont pas été intégralement rejoués.

SHA-256 APK debug : `7A0361F147B56BC31E0B3AE4E66824CCFF871171E9FB0DAF5261CFFF6F16FEE7`.

Les sources et tests ajoutés restent locaux, non commitées. Les captures et journaux sont dans `G:\DevServer\audits\TVFlow-Next-20261101`. Les sauvegardes des fichiers avant ce lot sont dans son sous-dossier `before`. Aucun profil, favori ou historique n’a été supprimé ; les essais de lecture modifient normalement la progression du film utilisé. Aucun runner cloud, aucune release et aucune modification de TVFlow original.

### Résultats visuels du build 226

Sur Mi Box, le même film « Diversa » présente deux scènes distinctes dans les captures 07/08 ; callback première frame **1920 × 924**, reprise à **50 005 ms**, puis checkpoints **60 920 → 75 952 ms**. L’écran noir reproduit avec 224 n’apparaît plus dans cet essai avec TextureView. Cela valide le rendu sur ce flux et cet appareil, sans certifier tous les codecs, la sortie audio ou une session longue.

L’appui central n’a pas fait apparaître les commandes dans l’essai 226 : la capture 08 reste une preuve vidéo, son nom de fichier « controls » n’est pas une preuve de contrôles affichés. Une correction complémentaire demande le focus de PlayerView et affiche son contrôleur après attachement sur TV ; elle est soumise au test suivant.

Sur l’émulateur, capture 10 : **six colonnes** dans Films, affiches au ratio 2:3, pas de débordement horizontal observé. Défilement réel capturé en 11, ouverture de « Love Language » en 12 et retour vers la grille Séries en 13, également à six colonnes. Les titres longs utilisent toujours deux lignes avec ellipse ; le panneau de catégories reste trop étroit pour certains libellés et l’en-tête peut encore être compacté. La recherche utilise la même grille adaptative, mais aucun nouveau scénario de saisie/recherche n’a été exécuté dans ce lot.

Démarrage froid émulateur 226 : **11 137 ms** par `am start -W`, puis accueil et catalogue affichés après attente. Mi Box : commande de lancement expirée à **10 171 ms**, puis navigation et lecture effectives ; ce timeout de commande n’est pas, à lui seul, une preuve d’ANR. Le temps jusqu’au catalogue réellement utilisable n’a pas été mesuré par instrumentation dédiée.

### Mesure de défilement — mauvais résultat, à ne pas confondre avec un gain démontré

Sur l’émulateur, build 227 debug, statistiques `gfxinfo` réinitialisées après ouverture de Films, puis huit gestes de défilement. Échantillon : **39 frames, 38 janky (97,44 %), médiane 350 ms, p90 900 ms**. PSS finale **250 914 Ko (~245 Mio)**, RSS **369 192 Ko (~361 Mio)**. Les huit gestes et la grille finale sont capturés ; ce n’est ni un gain avant/après mesuré ni une preuve de fluidité.

SurfaceFlinger indique **Google SwiftShader**, rendu logiciel de l’émulateur existant. Les percentiles GPU à 4 950 ms paraissent non exploitables pour une attribution précise et ne sont pas utilisés pour calculer un FPS. Le rendu logiciel est un facteur de contexte confirmé, pas une preuve que l’application n’a aucun défaut de performance. Aucun changement de configuration de cet AVD partagé avec d’autres projets. Prochain test : téléphone physique et Mi Box, parcours identique et profilage CPU/GPU, puis comparaison contrôlée.

### Télécommande TV — essai 227 et correction complémentaire

Le build 227 affiche le contrôleur natif. L’appui central a ouvert son menu de réglages : le focus initial n’était pas sur Lecture/Pause. Le test de pause a échoué, avec progression **98 445 → 113 501 ms**. Un second appui MEDIA_PAUSE n’a pas non plus établi de pause. Les images 14/15 ne sont donc pas présentées comme pause réussie.

Cause établie côté code : la gestion explicite des touches média dans la racine Compose était limitée à VLC ; la vue Android native n’avait pas de traitement explicite de ces touches et conservait la sélection de focus de son contrôleur. Le correctif complémentaire utilise une commande partagée vers le moteur actif dans les deux points de dispatch, consomme le relâchement de touche et empêche le double toggle lors d’un appui maintenu. Le focus TV est demandé sur `exo_play_pause` après affichage du contrôleur. Les touches sont ignorées localement pendant une session Cast active pour ne pas relancer un second moteur. Il conserve les commandes d’épisodes existantes et les contrôles Media3 natifs. Validation du build 228 ci-dessous.

### Commandes validées sur le build 228

La capture 18 affiche Lecture/Pause au centre avec focus visible. L’appui central met effectivement en pause, puis MEDIA_PAUSE est accepté sans redémarrage. Les trois checkpoints successifs portent **244 108 ms, playing=false** ; le test automatisé de stabilité de position réussit. Le retour Android est envoyé après la vérification. La capture 19 représente cette pause réelle. Ce succès remplace le défaut de prise de focus du 227 sur ce parcours ; la sélection des pistes et la navigation entre épisodes n’ont pas été rejouées.

Une dernière protection du build 229 ignore les commandes locales quand Cast est actif, pour éviter de relancer un lecteur local en parallèle. Le transfert Cast de bout en bout n’a pas été exécuté. Les essais de commande sont répétés sur le dernier APK installé ; leurs résultats suivent.

### Priorités après ce lot

| Priorité | Action restante | Preuve attendue |
|---|---|---|
| P1 | Démarrage lent et ANR intermittent A25 | Démarrages répétés hors compilation, plusieurs traces si blocage, réduction mesurée du temps jusqu’au catalogue utilisable |
| P1 | Fluidité A27 | Mesures physiques sur Mi Box et A54 ; comparer charge image, recomposition et rendu ; ne pas déduire le comportement physique de SwiftShader |
| P1 | Import Xtream A08, sécurité LAN A10/A11 | Import en mémoire bornée avec annulation et transactions ; coexistence originale/Next, appairage et refus des pairs inconnus |
| P1 | Téléphone portrait A04/A05 | Layout réellement adapté, orientation, clavier, insets et rotation depuis le lecteur ; pas un simple déverrouillage du manifeste |
| P2 | Ergonomie et cohérence visuelle | Catégories lisibles, en-tête mobile compact, panoramas vérifiés, composants partagés et focus/tactile cohérents |

TVFlow original vérifié inchangé : émulateur 1.0.156, dernière mise à jour le 29 octobre ; Mi Box 1.0.164, dernière mise à jour le 29 octobre. Aucun APK de production publié. Le code reste disponible dans le worktree vérifié avec toutes les modifications antérieures conservées.

### Résultat final du build 229

Même fichier sur Mi Box : première frame **1920 × 924**, reprise à **244 117 ms**, pause réelle via DPAD, puis deux checkpoints à **251 313 ms, playing=false**. Le test de pause stable réussit de nouveau sur le dernier APK. Les contrôles sont visibles, le focus central agit sur Lecture/Pause et les captures 20/21 présentent une image vidéo réelle. La lecture a été arrêtée après l’essai.

Le build 229 est confirmé installé sur émulateur et Mi Box. Le lancement final sur émulateur a de nouveau expiré côté `am start` à **10 757 ms** ; la capture 23 montre ensuite l’accueil, pas Films malgré son nom de fichier. L’appui de navigation prévu pendant le démarrage n’a donc pas validé l’ouverture de Films ; une seconde tentative après accueil est distinguée de cet essai. Le démarrage reste P1.

Validation locale finale : **157 tests unitaires, zéro échec ; zéro erreur lint, 43 avertissements et 7 indications ; `git diff --check` réussi** avec la configuration normale du dépôt. Aucun changement de configuration Git ni conversion globale des fins de ligne n’a été effectué.

### Dernière contre-vérification : A25 reproduit sur 229, performances physiques insuffisantes

La seconde tentative mobile ne valide pas Films : la capture 24 montre **un nouvel ANR sur l’accueil du build 229**. L’événement système indique `Input dispatching timed out` après 5 007 ms. La nouvelle trace place le thread principal Runnable dans **`ShaderBrush.obtainTransformShader` → `BackgroundNode.draw` → couches graphiques Compose**. Ce résultat localise cette occurrence dans le dessin ; il ne démontre pas à lui seul quel dégradé ou quelle invalidation en est la cause. Une mesure hôte après l’événement indique environ **16,7 Go disponibles sur 32,7 Go** : aucune saturation RAM hôte n’est établie. Le processus Next de l’émulateur a été arrêté après collecte, sans effacement de données. A25 reste P1 et est reproduit sur le dernier build, pas limité au seul 224.

Sur la **Mi Box physique**, build 229 debug, `gfxinfo` réinitialisé puis douze déplacements DPAD dans Films : **131 frames, 62 janky (47,33 %), médiane 17 ms, p90 150 ms, p95 250 ms, p99 400 ms**. PSS **130 490 Ko (~127 Mio)**. La capture 22 confirme que le parcours mesuré est bien la grille Films, avec focus sur une affiche. Ce parcours diffère des huit gestes tactiles de l’émulateur : ne pas comparer les deux pour calculer un gain. La médiane ne compense pas les nombreuses frames lentes ; **la fluidité cible n’est pas atteinte** sur cet essai physique.

Le prochain lot doit donc traiter en premier le coût du dessin et des invalidations de l’accueil/grilles, avec profils et comparaisons sur le même parcours, avant de multiplier les animations ou les panoramas. Maintenir les fonctionnalités et le contraste ; éviter une désactivation globale des effets présentée comme refonte premium. L’état final est un progrès fonctionnel vérifié, avec un blocage et des saccades explicitement ouverts ; aucune release recommandée.

## 14. Performances et validation des variantes optimisées — lots 230 à 236

Inventaire revérifié sur le PC Windows DESKTOP-8VD83BL : worktree G:\DevServer\worktrees\TVFlow-v2-Next-cinematic, branche feature/tvflow-next-cinematic-ui, HEAD initial 860b54b. Toutes les modifications antérieures conservées. Aucun runner cloud, commit, push ou publication. Le package installé reste com.tvflow.app.next ; mises à niveau adb install -r, sans désinstallation ni effacement de données. Tests avec le profil IPTV réel déjà configuré.

### Correctifs retenus et fichiers

| Problème | Cause racine | Fichier | Risque | Correction retenue | Validation |
|---|---|---|---|---|---|
| Accueil mobile mesuré comme une grande interface TV | Appel récursif du renderer TV avec surface imposée 1400 × 620 dp puis mise à l'échelle graphicsLayer | ui/dashboard/NextCinematicDashboard.kt | P2 : dimensions tactiles et texte incohérentes | Dessin dans le viewport réel ; marges, titre, navigation et raccourcis mobiles adaptés ; dégradés mémorisés | Accueil, collections, Films et fiche réels en paysage, captures 235 inspectées |
| Fond d'ambiance coûteux | Affiche agrandie avec blur dynamique | Même fichier | P2 : coût du dessin | Requête Coil cible 48 × 48 pour la couche d'ambiance, agrandissement FilterQuality.Low ; panorama et affiche de premier plan conservés | Capture réelle, aucune attribution exclusive du gain ni garantie de mémoire globale bornée |
| Chargement Live inutilisé dans l'accueil Next | observeAllChannels récupère les IDs puis les entités par lots de 250, mais les réunit toutes en map/liste ; plus de 22000 chaînes dans ce profil | ui/dashboard/DashboardViewModel.kt ; chemin existant dans repository/DAO | P2 : mémoire et concurrence SQL/CPU sans bénéfice visible | Pour le package Next uniquement, les trois flux Live hérités valent une liste vide ; préchauffage Live, boucle d'aperçu VOD et préchargement EPG du panneau invisible ne démarrent plus | Collections/favoris VOD conservés ; Direct conserve son modèle et affiche 58 chaînes de la catégorie essayée ; comparaison 234/235 ci-dessous |
| A28 : arrêt de l'application à l'ouverture libVLC minifiée | R8 retire/renomme la frontière Java appelée depuis JNI | app/proguard-next.pro ; app/build.gradle.kts | P0 : lecture libVLC impossible dans cette configuration | Conserver classes, interfaces et membres org.videolan.libvlc.** ; règles ajoutées uniquement pour TVFLOW_NEXT_BUILD=1 aux constructions optimisées Next | Reproduction 235, mapping R8 236 conservé, lecture réelle et commandes testées en 236 |

La suppression des tâches invisibles de l'accueil ne résout pas A08, l'import Xtream global avec DTO complets. Aucune affirmation que tout le catalogue de l'application est désormais borné en mémoire. Direct, Guide et Favoris conservent leurs requêtes propres ; aucune donnée utilisateur n'est supprimée. L'application originale installée n'a pas été mise à jour.

### Expériences retirées après mesure

Les variantes 230 à 233 ont essayé un délai de stabilisation du focus de 180 ms, son déplacement dans la carte, puis sa suppression, un zoom instantané API 28 et enfin le retrait de couches graphicsLayer. Aucune amélioration justifiée. MediaLibraryScreen.kt a été restauré exactement à sa sauvegarde antérieure au lot, qui inclut les corrections précédentes de grille et pagination. Ne pas réintroduire ces expériences comme des optimisations acquises.

Même Mi Box, même profil et catégorie initiale, mêmes douze déplacements DPAD, espacement 200 ms en plus de la latence ADB, gfxinfo remis à zéro avant le parcours. Deux essais par variante ; environnement Android/réseau non isolé. Le nombre de frames varie avec les animations ; ce ne sont pas des FPS.

| Build debug | Janky A / B | Médiane A / B | p90 A / B |
|---|---|---|---|
| 229 référence | 50,94 % / 53,05 % | 20 / 21 ms | 200 / 200 ms |
| 230 | 61,49 % / 63,52 % | 40 / 44 ms | 200 / 200 ms |
| 231 | 56,00 % / 53,05 % | 26 / 25 ms | 250 / 250 ms |
| 232 | 60,87 % / 59,62 % | 27 / 31 ms | 200 / 200 ms |
| 233 | 69,92 % / 72,87 % | 46 / 53 ms | 200 / 250 ms |

Une trace ART échantillonnée à 2000 µs est analysée localement sur le PC : environ 25,3 s, sans débordement déclaré, 130419 enregistrements du thread principal sans sortie non appariée. MediaPoster ~548,7 ms inclusifs, MediaLibraryScreen ~362,1 ms inclusifs ; temps qui se chevauchent et ne doivent pas être additionnés. Environ 88 ms exclusifs dans Throwable.nativeFillInStackTrace via ClassCastException sous DrawChildContainer.drawChild : coût observé, pas un crash utilisateur ni la cause prouvée de toutes les saccades. La trace avec surcoût de profilage ne sert pas de benchmark comparatif. Référence du format : sources primaires ART/AOSP runtime/trace.cc et trace.h. Trace brute conservée uniquement sur le PC, aucun argument ou URL authentifiée exposé dans le rapport.

### Variante de mesure proche release

Nouveau buildType perf, créé uniquement quand TVFLOW_NEXT_BUILD=1, hérite de release : R8 et réduction des ressources. Certificat debug explicitement conservé pour permettre la mise à niveau des installations de test sans effacer leur profil. Canal « Perf local », suffixe -perf, aucun usage du certificat de production. La variante n'est pas créée pour TVFlow original.

| Variante / essai | Frames | Janky | Médiane | p90 | p95 | p99 |
|---|---|---|---|---|---|---|
| 234 perf A, avant retrait des tâches invisibles | 139 | 69 (49,64 %) | 19 ms | 150 ms | 200 ms | 250 ms |
| 234 perf B | 149 | 65 (43,62 %) | 17 ms | 121 ms | 150 ms | 250 ms |
| 235 perf A, après | 124 | 60 (48,39 %) | 18 ms | 93 ms | 109 ms | 200 ms |
| 235 perf B | 127 | 59 (46,46 %) | 17 ms | 93 ms | 150 ms | 150 ms |

Le p90 est inférieur dans les deux essais 235, mais environ 47 % de frames en retard restent inacceptables pour une cible fluide. Aucune promesse de FPS ni gain global garanti. La bibliothèque et son zoom sont ceux de référence. Les mesures 235 ne sont pas rebaptisées 236 : ce dernier ajoute les règles JNI et reçoit une validation fonctionnelle distincte.

### Démarrage et ANR : amélioration mesurée, défaut encore ouvert

Sur l'émulateur API 35 avec rendu logiciel SwiftShader, après force-stop puis am start -W, attente et événement DPAD :

| Variante | Trois temps jusqu'à la première fenêtre | Résultat |
|---|---|---|
| 230 debug | 14810 / 7834 / 9375 ms | Aucun nouvel ANR détecté sur ces trois essais |
| 231 debug | 14363 ms, premier essai | ANR reproduit, arrêt des essais |
| 232 debug | 14287 / 6988 / 7614 ms | Aucun nouvel ANR sur ces trois essais |
| 234 perf | 9504 / 2969 / 2893 ms | Aucun nouvel ANR sur ces trois essais |
| 235 perf | 6835 / 2132 / 2201 ms | Aucun nouvel ANR sur ces trois essais |

ANR 231 : Input dispatching timed out, DPAD_UP attendu environ 5020 ms. Le premier commit était journalisé à 2291 ms, premier pré-dessin à 2492 ms ; le problème ne se limite donc pas au premier commit. L'intro vidéo et le préchargement d'images coïncidaient avec la période, sans démonstration de causalité exclusive. A25 reste ouvert. Les temps am start ne mesurent pas le catalogue prêt ou le panorama téléchargé ; premier lancement après installation, caches et profils ART influencent le résultat. Aucun téléphone physique connecté n'a été testé dans ce lot ; l'émulateur ne remplace pas ce contrôle.

### Lecture : le test réel a trouvé un P0 absent de la compilation

235 minifié, émulateur : Films → Possession → Lecture renvoie au lanceur Android. Logs natifs : FindClass(org/videolan/libvlc/interfaces/IMedia$Track) failed, JNI_ERR returned from JNI_OnLoad, puis System.exit called, status: 1. Le buffer crash Java était vide : il aurait manqué cet échec. Les contrôles introuvables sont enregistrés en échec, pas comme une lecture réussie.

236 conserve cette classe dans le mapping R8. Le nouveau parcours affiche une image réelle du film « A Possession in Lincoln County », avance de 11:51 à 12:01 après +10 s, ouvre le dialogue audio montrant Disable et Track 1 sélectionnée, puis reprend et progresse de 12:01 à 12:20. La sélection d'une autre langue n'est pas validée : une seule piste présente. Le premier contrôle de pause a commencé pendant la restauration de la position (00:00 puis 11:51) et n'a pas passé son assertion ; un contrôle après état prêt est réalisé séparément ci-dessous. Le premier PNG nommé playing ne montrait encore que le chargement : seules les captures ultérieures avec image constituent la preuve de décodage. Les logs conservent des avertissements HTTP initial, surface sous-titres et décodeur logiciel ; ils ne sont pas masqués ni considérés comme résolus.

235 Mi Box / Media3 : première frame 1920 × 924, image du film inspectée, reprise autour de 251329 ms, puis pause et deux checkpoints identiques à 260471 ms. DPAD et commande média fonctionnent sur ce parcours. Une nouvelle régression 236 est consignée séparément ci-dessous.

### UI et compatibilité

| Environnement / écran | Vérifié | Limite |
|---|---|---|
| Émulateur paysage 235 | Accueil avec panorama réel et actions, raccourcis défilants, collections, Films à six colonnes, fiche Possession et retour | Aucun pourcentage de fidélité attribué ; référence visuelle non nouvelle ; rendu logiciel |
| Téléphone portrait | Toujours verrouillé en paysage | Non validé, reste au backlog |
| Téléphone physique | Aucun disponible en ADB dans ce lot | Non testé |
| Mi Box Direct 235 | Catégories DPAD, 58 chaînes dans 4K UHD, logos et lignes visibles | Le nom de catégorie ne valide pas la lecture 4K ; TNT initiale affiche « Aucune chaîne TNT reconnue » |
| Mi Box grilles | Parcours DPAD et mesures répétées | Saccades importantes encore présentes |
| Lecteur mobile 236 | Image réelle, +10 s, audio et reprise avec progression | Pause après état prêt et autres pistes détaillées séparément ; sous-titres non certifiés |
| Live, épisodes, Groq, TMDB, mise à jour | Résultats antérieurs conservés dans les sections précédentes | Pas de nouvelle validation complète dans ce lot |

La première capture d'accueil 233 était noire : ne pas la compter comme un succès. Les captures 235 inspectées montrent effectivement accueil et collections. Aucun écran noir n'est utilisé comme preuve de lecture.

### Identité et contrôles de construction

Build 236 : 1.0.236-next-perf, versionCode 2020102236, package com.tvflow.app.next. SHA-256 ABB1B49AE4EF5CFE4264ADCA51F8CFC7DC758CF4655B0BA1503EEC0295F72CEC.
Build 235 comparé : SHA-256 268BCAF0C9ED86EE3DA613D7EA2D6CF4558C5423639BCC9F4438F9E6F137BA82.

236 : compilation minifiée locale réussie ; 157 tests unitaires, zéro échec et erreur ; lint perf zéro erreur, 36 avertissements, 7 indications. Les nombres lint diffèrent selon variante et ne sont pas revendiqués comme un gain de qualité. Package et certificat identique au 235 vérifiés avant installation ; adb install -r réussi sur émulateur et Mi Box. git diff --check passe. Les tests Room sur 25000 séries du lot 226 restent datés et ne sont pas présentés comme réexécutés ici.

### Plan priorisé

| Priorité | Action | Preuve attendue |
|---|---|---|
| P0 A28 | Conserver frontière JNI libVLC dans les builds optimisés Next | Image réelle, contrôles, absence des erreurs JNI reproduites ; résultats 236 ci-dessous |
| P1 A25 | Isoler tâches du démarrage et premier dessin | Profilage et répétitions sur téléphone physique ; trois essais sans ANR insuffisants |
| P1 A08 | Import Xtream et traitements bornés sans DTO complets en mémoire | Pic mémoire, temps et exactitude du catalogue sur gros profil |
| P2 A27 | Séparer coût SQL, images, composition et dessin sur Mi Box | Même variante optimisée de référence, p90/p95, allocations et régressions fonctionnelles |
| P2 UI | Portrait et composants réutilisables panorama/carte/focus/actions | Captures de chaque écran et format avant/après, DPAD et tactile |
| P2 Direct | Reconnaissance TNT et cohérence de la sélection | Catégorie reconnue ou état vide explicite et sélection synchronisée |

Aucune release prête n'est annoncée. Pas de publication de production. Profils, favoris et historiques conservés ; les reprises peuvent naturellement avancer lors des essais de lecture. Aucun identifiant IPTV, clé API ou URL authentifiée dans ce rapport.

### Validation finale du lot 236

- LibVLC sur émulateur : image réelle inspectée, avance +10 s, Track 1 visible, reprise 12:01 → 12:20. Contrôle séparé après état prêt : deux horloges à 12:33, bouton Reprendre, pause stable. JNI interface loaded présent ; aucun JNI_ERR, FindClass failed ni System.exit dans les logs du processus. Le P0 A28 reproduit en 235 n'est plus reproduit sur ce parcours 236. Cela ne certifie pas tous les appareils/ABI.
- Media3 sur Mi Box : première frame 1920 × 924 ; état READY et reprise à 260480 ms ; pause puis checkpoints identiques à 269786 ms espacés d'environ 15 secondes. Image du film et contrôles DPAD inspectés, script terminé. Le parcours 236 passe.
- L'application originale est revérifiée après installation : émulateur 1.0.156, dernière mise à jour 29 octobre 02:17:41 ; Mi Box 1.0.164, dernière mise à jour 29 octobre 22:20:04. Next est 1.0.236-next-perf / 2020102236 sur les deux appareils.
- Les mesures de démarrage et grille restent celles de 235. Pas de nouveau benchmark attribué à 236. ANR intermittent, fluidité insuffisante, import global et portrait restent ouverts.
- Rapport du lot et sept captures réduites sont intégrés à Audit-TVFlow-Next-236.html sur le PC ; originaux PNG, journaux, scripts de reproduction et APK dans G:\DevServer\audits\TVFlow-Next-20261101. La section 14 est ajoutée au rapport du dépôt sans écraser les sections antérieures. La copie jointe à la conversation n'a pas pu être actualisée, l'environnement de fichiers de la conversation étant devenu indisponible ; la copie PC est la référence pour ce lot.

## 15. Lot 237 — collectes de l’accueil, PC et émulateur uniquement

Consigne utilisateur appliquée : aucune commande ADB, installation ou interaction avec la Mi Box dans ce lot. Les captures Mi Box ajoutées au rapport correspondent exclusivement aux preuves antérieures 235/236. Travail sur le worktree et la branche Next revérifiés, changements non commités préservés. TVFlow original n’est ni installé ni mis à jour.

### A29 — P2 : requêtes de l’accueil dupliquées et panorama actif hors écran

Symptôme et cause vérifiée dans le code : les flux froids de recommandations étaient collectés à la fois par les collections et par une coroutine de panorama lancée en init. Les nouveautés étaient aussi créées deux fois. Chaque collecte indépendante redémarrait les observateurs Room et les transformations du repository. La coroutine init du panorama continuait tant que le ViewModel restait dans la pile de navigation, même sans collecteur de son état d’écran.

Risque : travail SQL, transformations et recherche TMDB concurrents inutiles, notamment lors du passage à Films. Ce défaut est établi par le graphe de collectes ; ce lot ne mesure pas directement le nombre d’exécutions SQL ni ne lui attribue à lui seul les ANR ou saccades.

Correction limitée à Next :
- DashboardViewModel.kt réutilise quatre flux partagés pour recommandations Films/Séries et nouveautés Films/Séries. Les variantes de TVFlow original conservent les flux froids existants.
- NextDashboardFlows.kt utilise shareIn avec un seul abonnement amont, replay de la dernière valeur pendant l’observation, arrêt et expiration du replay quand le dernier consommateur part. Le délai existant de 5 secondes de stateIn demeure ; aucun second délai n’est ajouté au partage.
- La recherche du panorama devient un flux froid transformLatest, collecté avec l’état. Elle annule la recherche précédente lors d’un changement et s’arrête avec les abonnements de l’écran, après leur délai de grâce. Une valeur null est émise avant la recherche pour ne pas attendre le réseau avant d’afficher les collections, ni associer l’ancien panorama à une nouvelle sélection.
- La configuration TMDB, les clés de sélection, la propagation de CancellationException, les favoris, données IPTV et actions de navigation restent en place. Aucun nouveau service, dépendance ou changement Room.

Fichiers : app/src/main/java/com/tvflow/app/ui/dashboard/DashboardViewModel.kt ; nouveau NextDashboardFlows.kt ; nouveau test NextDashboardFlowsTest.kt.

### Validation de la concurrence

161 tests unitaires passent, zéro échec et erreur. Les quatre nouveaux tests utilisent des flux comptabilisés et des canaux, sans sommeil arbitraire ni nouvelle dépendance :
- Aucun travail amont avant un abonnement.
- Deux consommateurs obtiennent les mêmes mises à jour depuis une seule collecte ; quitter le premier ne coupe pas le second ; quitter le dernier annule l’amont.
- Un retour après arrêt redémarre l’amont et ne reçoit pas son ancienne valeur en replay. Ce test simule des résultats successifs, ce n’est pas un test réel de changement de profil IPTV.
- L’annulation du scope du ViewModel interrompt un travail en cours.

git diff --check passe. La compilation minifiée et l’exécution sont consignées séparément ci-dessous.

### Référence de démarrage de cette session

L’émulateur était arrêté. Le même AVD SmartCourses_API35 est redémarré sur le port 5556, sans effacer ses données ni modifier sa configuration. Next 236 et sa playlist existante sont présents ; une capture réelle d’accueil 236 est inspectée.

Trois démarrages de référence 236 : am start -W = 587 / 536 / 527 ms jusqu’à la première fenêtre ; processus vivant et aucun nouvel ANR détecté après attente et DPAD. Ces valeurs diffèrent fortement des anciennes sessions, avant tout correctif 237 : elles montrent pourquoi les comparaisons entre sessions ne permettent pas de conclure à un gain du code. Le build concurrent sur le PC et les caches sont aussi des facteurs non isolés. Aucun temps « catalogue prêt » n’est déduit de ces chiffres.

### Identité du build 237

1.0.237-next-perf, package com.tvflow.app.next, versionCode attendu 2020102237. SHA-256 : 14B72A79E563EEA03F000987776BA41AFF1089E34ADDD9910A06CC970AD85908. Certificat identique au build 236 vérifié avant mise à niveau de l’émulateur par adb install -r. APK local de test, aucune publication.

### Résultats d’exécution 237

Compilation minifiée locale réussie ; lint perf : 0 erreur, 36 avertissements, 7 indications. Mise à niveau réussie sur l’émulateur uniquement.

| Test | Résultat | Portée |
|---|---|---|
| Démarrage froid ×3 | 1010 / 682 / 534 ms jusqu’à la première fenêtre ; aucun nouvel ANR détecté | Pas de gain démontré face à 236 ; réseau et charge PC non isolés ; pas un temps catalogue prêt |
| Films → retour accueil ×3 | Les trois cycles passent, données et actions présentes, processus vivant, aucun ANR ou FATAL EXCEPTION détecté | Attente sur Films supérieure au délai de grâce de l’état ; ne mesure pas directement les requêtes SQL |
| Accueil après retours | Panorama TMDB et actions réellement visibles sur la capture inspectée | La première capture froide 237-3 avait le fond noir malgré les données présentes ; ne pas la présenter comme une validation du panorama initial |
| Films | Grille d’affiches et catégories visibles sur capture inspectée | Aucun benchmark TV réalisé |
| Lecteur | Résultat détaillé après le parcours ci-dessous | La lecture 236 n’est pas automatiquement déclarée validée en 237 |

Point UI à surveiller : le panorama n’apparaît pas dans la capture froide 237-3, puis apparaît après les retours d’écran. Les logs indiquent pourtant result=tmdb. La cause n’est pas établie, et des captures noires transitoires avaient déjà été observées dans les lots précédents. L’amélioration du chargement initial des images reste ouverte ; pas de nouveau correctif spéculatif de dessin conservé ici.

### Lecture et clôture du lot 237

Le film de la playlist configurée est lu réellement avec libVLC : image vidéo inspectée, horloges 12:59 → 13:07 en lecture, puis 13:10 → 13:10 en pause. Aucun JNI_ERR, System.exit ou FATAL EXCEPTION détecté pour ce processus. Retour depuis le lecteur exécuté. Les autres langues, sous-titres et codecs ne sont pas revalidés par ce contrôle ciblé.

Next installé : 1.0.237-next-perf / 2020102237. TVFlow original sur émulateur reste 1.0.156, dernière mise à jour du 29 octobre à 02:17:41. Aucun accès à la Mi Box pour ce lot. Aucun effacement de données, aucune désinstallation, aucun commit/push ni release de production.

Validation retenue : correctif de collectes partagé et annulable, 161 tests unitaires, compilation et lint sans erreur, trois démarrages sans nouvel ANR détecté, trois cycles Films/Accueil et lecture réelle avec pause. Le gain global de fluidité et la disparition des ANR ne sont pas établis. Le chargement initial du panorama, A25, A27, l’import global et le portrait restent ouverts.

Le rapport joint est de nouveau actualisable dans cette session : les sections 14 et 15 et leurs captures datées sont ajoutées en conservant les captures et constats antérieurs. Les anciens résultats TV restent explicitement datés 235/236. Captures originales, journaux et APK 237 : G:\DevServer\audits\TVFlow-Next-20261101\lot237.

## 16. Tous les onglets — lots 238 à 242, téléphone paysage et Mi Box

Demande utilisateur : poursuivre vers une couverture complète de tous les onglets, avec autorisation renouvelée d’utiliser la Mi Box. Dépôt, branche et état Git revérifiés sur DESKTOP-8VD83BL ; aucun AGENTS.md trouvé dans le worktree. Les modifications antérieures sont conservées. Développement, Gradle et ADB uniquement sur le PC Windows ; aucun runner cloud.

### Corrections et preuves

| Anomalie | Symptôme et cause | Fichiers | Risque | Correction retenue | Validation |
|---|---|---|---|---|---|
| A30 — navigation incohérente entre onglets | Plusieurs pages sans navigation directe ; Recherche de l’accueil ouvrait seulement l’IA | ui/navigation/TVFlowNavHost.kt ; nouveaux NextSection.kt et NextSectionNavigation.kt ; DashboardScreen.kt ; NextCinematicDashboard.kt ; GuideScreen.kt | P2 : parcours et focus incohérents | Barre Next commune aux sections principales, état actif bleu, bordure de focus claire, hauteur tactile minimale 48 dp, défilement horizontal ; accès Recherche locale et IA séparés ; rail Guide redondant masqué dans Next | Captures réelles de chaque section sur émulateur paysage et Mi Box ; DPAD TV exécuté |
| A30b — restauration d’onglet erronée pendant le lot 238 | Accueil pouvait restaurer le dernier onglet ; Films/Séries partagent un identifiant de destination et pouvaient restaurer les mauvais arguments | TVFlowNavHost.kt | P1 : mauvais écran après activation | Retour Accueil par popBackStack vers dashboard ; autres sections sans restauration par ID, pile bornée au dashboard, launchSingleTop ; pas de duplication de destinations | Échec réellement observé en 238, correction 240 ; retour Accueil vérifié sur émulateur, captures TV datées |
| A31 — recherche sans état d’erreur explicite | Recherche locale peu accessible ; échecs assimilés à zéro résultat ; annulations capturées trop largement dans l’ancien chemin | SearchScreen.kt ; SearchViewModel.kt ; nouveau NextSearchState.kt | P1/P2 : silence en cas d’échec, information ambiguë | Chemin Next avec chargement, résultat vide et erreur distincts ; attente 180 ms annulable ; nouvelle saisie annule la précédente ; aucun détail privé dans le message d’erreur | Quatre tests unitaires ; recherches réelles avec correspondances et sans résultat ; clavier et retour |
| A32 — suggestions Films non bornées | VodDetailsViewModel observait toute la catégorie, triait en Kotlin puis prenait dix éléments | TVFlowDao.kt ; XtreamRepository.kt ; VodDetailsViewModel.kt | P2 : allocations inutiles sur gros catalogue, risque mémoire | Requête Room LIMIT 10, filtrée par profil/catégorie, excluant le film courant ; tri date/ajout/nom/ID en SQLite ; chemin Next uniquement, observation conservée | Deux tests Room sur 25000 films sur chaque appareil, en base en mémoire indépendante |
| A33 — faux message TNT vide pendant chargement | Le cas TNT affichait « Aucune chaîne… » dès que la liste était vide, même pendant le chargement | HomeScreen.kt | P2 : faux diagnostic de catalogue vide | Message de chargement prioritaire dans Next tant que isLoading | Capture Direct 240 avec 25 chaînes sur les deux appareils ; absence de prétention à résoudre tout le coût d’hydratation Live |
| A34 — contraste de Recherche | En-têtes Films/Séries/Live héritaient d’une couleur noire sur fond sombre ; titres longs pouvaient concurrencer le type | SearchScreen.kt | P2 : lisibilité et focus | 241 : en-têtes clairs, cartes bleu profond, focus bordé, titre borné à deux lignes avec place réservée au type | Capture 240 établissant le défaut ; contrôle visuel 241 distinct ci-dessous |

Le composant de navigation est masqué sur connexion, fiches et lecteurs afin de préserver leurs parcours et le plein écran. L’accueil garde son bandeau cinématographique et ses raccourcis fonctionnels. Pas de nouvelle dépendance, pas de migration Room, pas de remplacement par une maquette. Les changements de présentation et de comportements nouveaux sont conditionnés au package Next ; les APK de TVFlow original ne sont pas mis à jour.

### Ce que les tests ont réellement démontré

169 tests unitaires passent. Les huit nouveaux tests couvrent les correspondances des neuf destinations, la distinction des arguments Films/Séries, l’absence de barre sur les lecteurs/fiches, la séparation Recherche/IA, les requêtes courtes, les états chargement/erreur, l’annulation de la recherche précédente et l’absence d’erreur utilisateur lors d’une annulation.

La première exécution comptait un échec de test : l’assertion attendait à tort qu’une CancellationException du flux enfant soit propagée au collecteur de flatMapLatest. Le test a été corrigé pour vérifier le comportement attendu — aucun résultat d’erreur publié. Ce n’était pas présenté comme une réussite avant correction. Les rapports XML finaux, et non seulement le code de retour du shell, établissent 169 tests sans échec.

Room est testé avec le build debug 239 (classes non obfusquées nécessaires aux tests instrumentés), puis la variante minifiée est réinstallée sans désinstallation :
- Émulateur : OK, 2 tests ; catégorie 25000 lignes, dix entités hydratées, requête 8 ms.
- Mi Box : OK, 2 tests ; mêmes assertions, requête 198 ms.
- Ordre, exclusion de l’élément courant, séparation profil/catégorie, catégories absentes et mise à jour des métadonnées vérifiés. Les données synthétiques sont dans une base en mémoire ; aucune insertion dans le catalogue utilisateur.
- Ces durées portent sur un essai synthétique court, pas sur tout le catalogue réel et pas sur un gain de FPS. SQLite peut encore parcourir/trier des lignes ; la correction borne l’hydratation Kotlin, pas tout le coût CPU du moteur SQL.
- Aucun nouveau test instrumenté complet n’est attribué abusivement au build minifié 241 ; la requête testée est inchangée dans 240/241.

### Matrice de navigation et d’affichage

| Écran | Émulateur téléphone paysage 240 | Mi Box 240 | Écart ou limite restant |
|---|---|---|---|
| Accueil | Panorama, actions et raccourcis visibles, retour depuis les sections vérifié | Accueil cinématographique capturé | Panorama initial intermittent ; la capture TV utilise aussi une affiche de secours, sans preuve de panorama TMDB validé dans ce cas |
| Direct | 25 chaînes TNT et logos visibles | 25 chaînes TNT et logos visibles après chargement, focus de barre visible | Hydratation Live et aperçu réel restent à profiler ; lecture Live non revalidée par la seule liste |
| Guide TV | Grille EPG, logos, programmes et heures visibles | Navigation DPAD et capture de section ; une capture 238 montre les programmes, une capture 240 initiale reste vide pendant le chargement | Attente et état de chargement à améliorer ; pas de validation EPG complète déduite d’une page initialement vide |
| Films | Grille et catégories visibles ; accès fiche via recherche vérifié | Bandeau de sélection, catégories et affiches visibles ; focus onglet vérifié | Bandeau encore différent du panorama de référence ; lecture TV de ce lot distincte des anciens succès |
| Séries | Grille visible ; fiche réelle et Saison 1 chargées depuis la playlist | Bandeau de sélection et affiches visibles ; focus onglet vérifié | Pas de nouvelle validation de lecture d’épisode ni de passage automatique au suivant |
| Recherche locale | Requête Possession avec résultats ; requête impossible avec état vide ; retour clavier | Page accessible au DPAD et capture réelle | Contraste corrigé ensuite en 241 ; erreurs réelles réseau non provoquées dans le catalogue utilisateur |
| IA | Requête naturelle réelle et résultat local affiché | Page et suggestions accessibles au DPAD | Compteur Groq reste à zéro ; ne valide pas un appel Groq |
| Favoris | Ajout d’un film, affichage, persistance après redémarrage, suppression et retour à l’état initial réussis | Page et filtres visibles, navigation DPAD | Synchronisation entre appareils non revalidée |
| Réglages | Menu des rubriques accessible et capturé | Menu accessible au DPAD et capturé | Aucun changement de compte, restauration de sauvegarde, installation de mise à jour ou réglage destructif déclenché |

Cette matrice vérifie l’accès et des parcours ciblés ; elle ne déclare pas chaque fonction de chaque onglet à 100 %. Portrait et téléphone physique ne sont pas validés dans ce lot. Les sections antérieures conservent leurs résultats datés.

### Favoris et services réels

La recherche naturelle « une comedie francaise » termine avec un résultat du catalogue et l’indication « Recherche locale » ; zéro requête Groq est affichée. Le succès du moteur distant n’est donc pas établi.

La fiche de « Ágata y Lola » montre Saison 1 après chargement réel. La présence d’une saison ne constitue pas une lecture d’épisode réussie.

Le film choisi pour le test de favoris n’était pas favori au départ. Il a été ajouté, retrouvé dans Favoris → Films, encore présent après arrêt/redémarrage, puis retiré ; son absence finale est vérifiée. Aucun favori préexistant n’a été supprimé. Profils et historiques sont conservés ; des reprises peuvent naturellement avancer lors des essais de lecture.

### Qualité du banc de test

Le helper ancien pouvait relire /sdcard/next-ui.xml lorsque uiautomator échouait tout en retournant zéro. Un nouveau helper supprime le fichier avant chaque relevé et exige un XML frais. Les premiers faux échecs sont conservés comme limites du banc, sans les transformer en bugs produit.

Sur Mi Box, certaines captures d’accessibilité restent indisponibles faute d’état idle. Les captures ADB réelles et les commandes DPAD sont alors examinées directement ; aucun XML périmé n’est utilisé. Les relevés automatiques et les vérifications visuelles sont distingués. Une tentative de parcours VOD TV de ce lot n’a pas atteint un état de lecture démontré : elle ne remplace pas les validations Media3 des builds 235/236.

### Identité et compilation

238/239/240 sont des étapes locales. Le build intermédiaire après correction de contraste est 1.0.241-next-perf, package com.tvflow.app.next, versionCode attendu 2020102241. SHA-256 : A124CD38AF9208BC4D2BC669E4119AF3C8112FD306859DE09CEE8105346B45A1.

Build 241 minifié local : BUILD SUCCESSFUL, 169 tests unitaires, zéro échec ; lint final et exécution ci-dessous. Package et certificat identique au certificat de test précédent vérifiés avant adb install -r, installation réussie sur émulateur et Mi Box. Aucun APK de production publié ; aucun push ni commit imposé. Les branches, modifications existantes et données utilisateur sont préservées.

### Priorités pour poursuivre l’objectif complet

| Priorité | Suite nécessaire | Critère de fin |
|---|---|---|
| P1 | ANR intermittent A25, import global A08, charge Live/EPG | Mesures répétées sur gros profil, mémoire bornée, absence de blocage démontrée sur échantillon documenté |
| P1 | Parcours complets lecteurs et séries sur la dernière variante | Live, VOD, épisode, reprise, audio/sous-titres et épisode suivant, images et logs natifs |
| P2 | Portrait et téléphone physique | Captures et interactions tactiles de chaque onglet, clavier, rotation et retour lecteur |
| P2 | Panorama TMDB et cohérence de présentation | Comparaison visuelle réelle aux références, chargement/fallback explicitement testés sur chaque appareil |
| P2 | Recherche distante Groq | Requête distante authentifiée démontrée avec gestion du quota et erreurs, aucun secret exposé |
| P2 | Réglages, sauvegardes, mises à jour Next | Tests ciblés réversibles, intégrité APK et données conservées ; aucun succès déduit de la seule page de menu |

### A25 / A35 — ANR réel du Guide sur Mi Box en 241, puis correctif ciblé 242

Le dernier essai 241, après ouverture du Guide depuis un lancement froid, affiche réellement « TVFlow-v2 Next ne répond pas ». Capture conservée. Le rapport de dernière ANR indique une attente de traitement d’événement clavier le 1er novembre à 22:11:40 sur la Mi Box. Le PID du processus Next est 20320. Ce résultat invalide toute prétention de stabilité complète du Guide en 241.

Le bugreport et les traces brutes restent privés sur le PC. L’extraction est restreinte au bloc de processus Cmd line: com.tvflow.app.next et au thread principal ; les autres processus ne sont pas utilisés pour expliquer cet ANR. La trace à 22:11:41 montre :
- java.util.regex.Pattern.compile et le constructeur de Regex sur le thread main ;
- gc2.a, identifié dans le mapping R8 241 comme LiveChannel.isSectionMarker, ligne 69 de Models.kt ;
- x20.b, identifié comme ChannelSortingKt.selectTntChannels ;
- collecte de ce flux depuis le contexte principal du Guide.

La détection de séparateur recompilait le même motif à chaque accès, pour chaque chaîne. La sélection TNT travaille sur le catalogue Live avant de le réduire à quelques chaînes ; les traitements du Guide étaient exécutés dans le contexte principal. Ce chemin coûteux est observé dans la trace de blocage. Il ne prouve pas qu’il explique tous les ANR antérieurs, dont certains concernaient le dessin Compose.

242 apporte trois corrections limitées :
- Models.kt conserve exactement le même motif de séparateur dans une valeur privée, au lieu de le compiler à chaque appel. Reconnaissance et titre de section inchangés.
- GuideViewModel.kt déporte les transformations des catégories, des chaînes et de l’état présenté sur Dispatchers.Default dans Next, avant leurs stateIn. Les mises à jour d’état restent des StateFlow ; les actions de lecture et la règle des deux appuis ne sont pas changées.
- HomeViewModel.kt ne collecte plus l’intégralité du catalogue pour un aperçu fermé dans Next. Un identifiant d’aperçu absent produit null ; lorsqu’il existe, seule la chaîne choisie est observée avec les identifiants existants. Le chemin original est conservé pour les builds non Next.

Build 242 : 1.0.242-next-perf, versionCode attendu 2020102242, SHA-256 B668CEB57B1C64D096D6B6AAA1CA90879FCD9F2C780EF4C338E2D08E39A19B7D. Compilation minifiée locale réussie, 169 tests sans échec, lint zéro erreur, 36 avertissements et 7 indications. Package et certificat vérifiés ; mise à niveau sans effacement de données. Les reprises du scénario et leurs limites sont détaillées ci-dessous.

### Lecture et contraste vérifiés en 241

La capture après correction montre l’en-tête Films et les cartes de résultats lisibles sur fond sombre. Le film de la playlist est réellement décodé avec libVLC : progression 13:37 → 13:45, puis deux horloges identiques à 13:49 en pause. Image inspectée ; aucun JNI_ERR ou FATAL EXCEPTION détecté pour ce processus. Cela ne valide pas les autres codecs, pistes ou appareils.

La première tentative automatisée VOD TV 240 n’avait pas démontré une lecture : le script n’avait pas atteint le lecteur et le relevé d’accessibilité a échoué. Aucun écran d’accueil ou de favoris n’est compté comme preuve de lecture. Les succès Media3 235/236 restent historiques.

L’application originale est revérifiée : émulateur 1.0.156, dernière mise à jour du 29 octobre à 02:17:41 ; Mi Box 1.0.164, dernière mise à jour du 29 octobre à 22:20:04. Aucune publication ni modification de ces installations.

### Reprises ciblées après le correctif 242

Trois lancements froids sur Mi Box, puis ouverture Guide, attente de 35 secondes et passage vers Films au DPAD : trois processus restent vivants, aucun nouvel événement ANR détecté pour leurs PID. Les captures montrent la grille EPG chargée et le passage effectif vers Films. Le défaut 241 n’est pas reproduit dans cet échantillon. Cela ne certifie pas tous les parcours : A25 reste sous surveillance, avec les traces et la capture d’échec conservées.

Sur émulateur en 242, la recherche corrigée est exécutée et le même flux libVLC est lu réellement : progression 14:15 → 14:23, puis pause à 14:27 sur deux relevés. Aucun JNI_ERR ou FATAL EXCEPTION détecté pour ce processus. L’image est inspectée. L’aperçu Live à deux appuis conserve son contrôleur et ses tests unitaires ; son décodeur n’est pas revalidé par ce seul parcours VOD.

Le build de test 242 est installé sur les deux appareils sans désinstallation. La nouvelle requête Room est inchangée depuis ses tests instrumentés 239. Aucun nouveau benchmark global de fluidité n’est attribué à ces changements : les anciennes mesures de saccades ne sont pas effacées. Aucun téléphone physique n’est connecté lors de l’inventaire final ; seuls Mi Box et émulateur sont présents en ADB.

Objectif complet encore non atteint : portrait, Groq distant, mise à jour/sauvegardes et couverture exhaustive des lecteurs demeurent à valider ; la cohérence des panoramas et certaines présentations des fiches restent à améliorer. Aucune release de production publiée.

### Validation VOD TV 242 finalement exécutée

Le parcours est repris pas à pas au DPAD : Films, catégorie, affiche Diversa, fiche puis Lecture. Le lecteur s’ouvre réellement. Deux captures montrent des images distinctes du film à 04:41 puis 04:46 ; la seconde affiche la commande Reprendre après la pause. Les journaux Media3 donnent l’état READY, playing=false et deux checkpoints identiques à 285542 ms, espacés d’environ 15 secondes. La lecture et la pause sont donc validées pour ce flux en 242. Le marqueur firstFrame n’est pas présent dans l’extrait final récupéré ; aucune résolution vidéo nouvelle n’en est déduite.

Cette réussite complète la tentative automatisée 240 qui n’avait pas atteint le lecteur. Elle ne valide pas encore tous les codecs, pistes, sous-titres, épisodes ou flux Live. Les essais de lecture sont arrêtés à la fin, sans effacer les profils ni les historiques.

### Résultat retenu du lot

Build de test final : 1.0.242-next-perf / 2020102242 sur émulateur et Mi Box. 169 tests unitaires sans échec ; quatre succès instrumentés Room répartis sur les deux appareils au build debug 239 ; zéro erreur lint, 36 avertissements, 7 indications ; git diff --check passe. Le scénario Guide qui a reproduit l’ANR 241 passe trois fois en 242, avec captures de grille et de sortie vers Films. VOD libVLC sur émulateur et Media3 sur Mi Box : image et pause réellement validées.

Les nouveaux éléments améliorent l’accès aux onglets, le retour Accueil, la séparation Films/Séries, la recherche, les suggestions et le traitement du Guide. Aucun pourcentage de complétude ou de fidélité graphique n’est annoncé. La matrice garde les fonctions non testées et le backlog ouvert ; les mesures antérieures de saccades restent valables comme problèmes à résoudre. Aucun runner cloud, aucune release de production, aucun effacement de données, aucun remplacement de TVFlow original.


## 17. Lecture des séries et Live — lot 243 du 1er novembre

Version installée sur les deux appareils : **1.0.243-next-perf**, versionCode **2020102243**, package **com.tvflow.app.next**. Travail local Windows sur la branche feature/tvflow-next-cinematic-ui ; modifications précédentes conservées. Compilation minifiée locale réussie en 1 min 44 s. **172 tests unitaires sans échec ; lint : zéro erreur, 36 avertissements et 7 indications.** git diff --check passe.

SHA-256 de l’APK de test : 596714EF9B6ECDDED3E64B2B3FAA7361ADBFFE4A0974EB2AA3945BFB9E482F81. APK conservé sur le PC : G:\DevServer\audits\TVFlow-Next-20261101\lot243\TVFlow-v2-Next-1.0.243-perf.apk. Mise à niveau avec conservation des données ; aucune release publiée.

### Défaut confirmé et correction limitée

Pendant l’essai initial 242, un passage inattendu de S01E02 vers S01E03 est observé. Sa cause d’exécution n’est pas établie : les anciens événements libVLC ne sont pas disponibles dans les journaux récupérés. Cette observation ne suffit pas à attribuer le passage au réseau ou au fournisseur.

L’inspection confirme cependant deux faiblesses : le callback libVLC appelait onPlaybackEnded sans transmettre sa durée, et le modèle considérait tout signal de fin comme une lecture achevée. Il marquait le contenu terminé et pouvait ouvrir l’épisode suivant, sans vérifier la position réelle.

| Anomalie | Symptôme et cause | Fichiers | Risque | Correction 243 | Validation et limite |
|---|---|---|---|---|---|
| A36 — P1 | Durée libVLC perdue à la fin ; aucun contrôle d’une fin anticipée | ui/vod/VodPlayerScreen.kt, VodPlayerViewModel.kt, nouveau PlaybackCompletion.kt | Historique terminé à tort et enchaînement indu en cas d’EOF prématuré | Transmettre position/durée libVLC et Media3 ; dans Next, refuser la complétion si plus de 2 s restent pour une durée connue, conserver la progression et afficher une erreur. Journal de fin limité aux durées, sans URL | Trois tests : EOF à 24 s sur 50 min, fin normale/arrondi, durée inconnue. Lecture/pause/passage manuel/reprise réels validés. Une coupure réseau réelle et une fin naturelle complète ne sont pas validées |
| A37 — P2, à diagnostiquer | Des zones de navigation et de catégories deviennent noires sur une capture d’aperçu Live 242 | ui/guide/GuideScreen.kt : LivePreview ; ui/home/HomeScreen.kt ; composition vidéo | Interface partiellement illisible pendant l’aperçu | Aucun changement spéculatif de moteur ou de surface | Le nouvel essai 243 affiche navigation et catégories correctement avec l’image vidéo. Défaut intermittent non déclaré résolu ; comparer captures et rendu physique |
| A38 — P2, ouvert | Titre et commandes libVLC parfois rognés aux bords sur émulateur | ui/common/VlcVodControls.kt, VlcFallbackPlayer.kt, ui/vod/VodPlayerScreen.kt | Lisibilité et commandes masquées | Correction de composition à isoler ; comparer sur téléphone physique avant de généraliser | Captures 243 de pause/reprise conservées avec le défaut visible. La lecture réussie ne valide pas la présentation |

La protection A36 conserve le comportement du moteur lorsque la durée est inconnue : ce cas reste une limite explicite. Le garde-fou ne démontre pas que la cause du changement inattendu observé en 242 est éliminée. Le nouveau journal de fin facilite sa prochaine reproduction.

### Résultats exécutés avec la playlist configurée

| Parcours | Appareil / version | Résultat observé | Portée |
|---|---|---|---|
| Série ES - Ágata y Lola, saison 1 | Émulateur paysage, 243 | Fiche réelle, épisodes issus du serveur, lecture libVLC avec image | Un titre, pas tous les épisodes du catalogue |
| Pause épisode 1 | Émulateur, 243 | Deux relevés 00:23 / 49:43 identiques, espacés de 6 s ; bouton Reprendre et image visibles | Pause tactile validée ; événement matériel MEDIA_PAUSE non certifié dans ce parcours |
| Épisode suivant explicite | Émulateur, 243 | Le bouton ouvre S01E02, image réelle, pause à 00:59 / 50:30 | Succès après reprise du script : un premier essai ne trouvait pas le bouton car les commandes avaient été masquées ; cet échec d’automatisation n’est pas compté comme succès |
| Reprise épisode 1 | Émulateur, 243 | Après sortie et réouverture depuis la fiche, S01E01 est observé à 00:39, après lecture depuis la progression sauvegardée à 00:23 | Reprise dans la même session ; redémarrage complet et fin de saison non testés |
| Live : sélection, aperçu, plein écran | Mi Box au DPAD, 242 puis 243 | Premier appui sans vidéo ; deuxième appui ouvre ensuite l’aperçu ; troisième ouvre le lecteur. Images réelles distinctes | Le délai exact n’est pas mesuré au chronomètre ; contrôleur de délai 2 s et ses tests unitaires conservés |
| Live final après installation | Mi Box, 243, processus 22892 | Media3 READY, playing=true, videoSize=1920x1080, firstFrame=true dans les checkpoints ; position 6317 puis 25636 ms. Aucun FATAL EXCEPTION ni JNI_ERR dans le journal de ce PID | Le marqueur de callback rendered-first-frame est absent de l’extrait final ; le statut des checkpoints et les images constituent les preuves. Le nom TF1 4K ne prouve pas une source 4K |
| Retour depuis Live | Mi Box, 243 | Retour à Direct, navigation visible ; catalogue encore en chargement sur la capture à 3 s | Retour déclenché, disponibilité immédiate des chaînes non validée |
| Tour des onglets | Mi Box, 243 | Accueil, Direct, Guide, Films, Séries, Recherche, IA, Favoris et Réglages ouverts au DPAD ; captures PC | Navigation uniquement. Direct à 5 s et Guide à 10 s sont encore en chargement : aucune grille EPG chargée n’est déduite de ces images. Les trois essais Guide 242 restent les preuves précédentes |
| Original TVFlow | Deux appareils | Émulateur 1.0.156 / 29 octobre 02:17:41 ; Mi Box 1.0.164 / 29 octobre 22:20:04 | Versions et dates de mise à jour inchangées |

Les commandes Live de validation s’exécutent après l’arrêt du lecteur de séries de l’émulateur. Aucun identifiant IPTV, serveur ou secret n’est inclus dans ce rapport. Les essais Next sont arrêtés à la fin sans effacer les historiques.

### Comparaison visuelle complémentaire et suite priorisée

La cible reste la description et les références inventoriées précédemment ; aucune nouvelle image de référence n’est fournie dans ce lot. Les captures réelles permettent de relever les écarts sans annoncer une fidélité de 100 %.

| Écran | Référence | État actuel | Écarts | Priorité |
|---|---|---|---|---|
| Accueil TV | Panorama, navigation, raccourcis et rails | Panorama et collections visibles sur la capture 243 | Cohérence avec les autres onglets encore incomplète | P2 |
| Direct | Catégories lisibles, aperçu intégré et focus | Aperçu et plein écran réels en 1080p ; focus bleu | Rendu noir intermittent 242 ; retour 243 encore en chargement à 3 s | P2 |
| Films et Séries | Grand panorama et collections cinématographiques | En-tête à affiche, résumé et grille | Absence du grand panorama équivalent à Accueil ; mutualisation visuelle à poursuivre | P2 |
| Lecteur séries | Commandes lisibles, progression et navigation | Lecture, pause, suivant et reprise fonctionnent sur le flux testé | Bords parfois rognés sur émulateur ; audio/sous-titres de série non validés | P2 |
| Guide / Recherche / IA / Favoris / Réglages | Navigation cohérente et parcours complets | Barre commune et écrans accessibles | Couverture fonctionnelle exhaustive encore absente ; Groq distant et sauvegardes/mises à jour restent ouverts | P1/P2 |

Ordre de poursuite : reproduire les interruptions et tester la vraie fin d’épisode (A36), borner l’import et les traitements Live/EPG (A08/A25), corriger la composition des lecteurs (A37/A38), achever portrait et téléphone physique, puis uniformiser les panoramas avec des composants Compose communs. Tester ensuite pistes audio/sous-titres, fin de saison, Groq distant et sauvegarde/mise à jour avec conservation des données. Les anciennes mesures de saccades restent dans le rapport ; aucun gain global de fluidité n’est annoncé sans nouveau benchmark comparable.

L’objectif de 100 % sur tous les onglets n’est pas encore atteint. Ce lot apporte une correction de stabilité de lecture, 3 tests supplémentaires et de nouvelles validations réelles ; les fonctions non testées et les anomalies visuelles restent visibles.

## 18. Accueil sans navigation redondante — lot 244, validation du 2 novembre 2026

**L’accueil Next utilise désormais une seule barre de navigation**, identique à celle des autres onglets. Le menu latéral et la rangée « Applications favorites » ne sont plus affichés dans Next. Le panorama, les rails et les actions propres au contenu — Voir maintenant, Ma liste et Détails — restent disponibles.

### A39 — P2 : plusieurs groupes de sélection pour les mêmes destinations

| Symptôme | Cause vérifiée | Fichiers concernés | Risque | Correction | Test de validation |
|---|---|---|---|---|---|
| Direct, Guide, Films, Séries et Réglages apparaissaient en haut et dans les raccourcis ; Favoris avait un autre accès latéral | NextCinematicDashboard composait trois groupes de navigation. TMDB et Réglages appelaient le même callback ; plusieurs liens latéraux ouvraient simplement la bibliothèque sans filtre spécifique | ui/dashboard/NextCinematicDashboard.kt ; DashboardScreen.kt ; ui/navigation/TVFlowNavHost.kt | Écran chargé, parcours DPAD inutilement long, destinations ambiguës | Afficher NextSectionNavigation également sur Accueil ; désactiver les groupes internes avec unifiedNavigation uniquement pour le package Next. Reprendre la largeur du menu latéral pour le contenu ; éviter une seconde marge de barre système sur mobile | Captures avant/après ; un exemplaire de chacun des neuf libellés dans la hiérarchie réelle sur les deux appareils ; parcours tactiles et DPAD détaillés ci-dessous |

La variante non Next conserve le rendu antérieur : le composant de tableau de bord est partagé avec son chemin TV, ce qui justifie une condition explicite plutôt que la suppression globale de ses contrôles. Aucune réécriture de la navigation, aucun changement de dépendance, de requête réseau, de profil ou de base de données dans ce lot. L’accès TMDB reste disponible dans Réglages. La recherche locale, l’IA et les Favoris disposent chacun de leur onglet unique.

### Build et tests réellement exécutés

Version de test **1.0.244-next-perf / 2020102244**, package com.tvflow.app.next. Compilation locale Windows terminée le 1er novembre ; installation et validation reprises le 2 novembre. Branche feature/tvflow-next-cinematic-ui et modifications Git préexistantes vérifiées. **BUILD SUCCESSFUL en 1 min 43 s ; 172 tests unitaires sans échec ; lint : zéro erreur, 36 avertissements, 7 indications ; git diff --check passe.**

| Contrôle | Émulateur paysage | Mi Box au DPAD |
|---|---|---|
| Barre unique sur Accueil | Accueil, Direct, Guide TV, Films, Séries, Recherche, IA, Favoris et Réglages apparaissent une fois chacun | Même comptage réel, un exemplaire par libellé |
| Anciens groupes | Applications favorites et ancien raccourci TMDB absents ; captures inspectées | Menu latéral et raccourcis absents ; captures inspectées |
| Navigation et retour | Films, Séries, Favoris et IA ouverts, puis retour Accueil avec action Voir maintenant visible après chaque parcours | Films ouvert et vérifié par son champ de recherche, puis retour Accueil |
| Actions du panorama | Détails ouvre le dialogue ; Fermer ramène à l’accueil | Bas donne un focus visible sur Ma liste sans l’activer ; Droite puis OK ouvre Détails ; Retour ferme le dialogue |
| Journaux du processus testé | Aucun FATAL EXCEPTION ni JNI_ERR trouvé pendant ce parcours | Même contrôle sans erreur de ces deux types pendant ce parcours |
| Limites | Téléphone physique et portrait non testés ; aucun nouveau test de décodeur vidéo | Pas de nouveau test Live/VOD dans ce lot de navigation ; les preuves 243 restent datées au chapitre 17 |

Le premier script mobile a échoué parce qu’il exigeait le titre Diversa, propre à une autre sélection du catalogue. Une lecture fraîche de la hiérarchie a confirmé la bibliothèque Films et son champ de recherche. Le script corrigé vérifie la destination et passe pour les quatre parcours. L’échec initial est conservé sur le PC ; aucune modification d’application n’a été nécessaire pour le résoudre.

Une expiration de connexion au PC a aussi interrompu le suivi d’installation. Les versions installées ont été relues avant de lancer les tests, pour éviter de confondre une commande envoyée avec une installation confirmée.

APK local : G:\DevServer\audits\TVFlow-Next-20261101\lot244\TVFlow-v2-Next-1.0.244-perf.apk.
SHA-256 : **0E99303CC0466B925277D9E4EA251CDF69E86F62D07101E7B1DEFA61DB11C77C**.

Installation par mise à niveau, sans désinstallation ni effacement. Les captures avant/après montrent le favori préexistant du contenu vedette toujours marqué « Dans ma liste » sur l’émulateur ; aucun bouton d’ajout/suppression de favori n’a été activé par ces tests. L’application originale est revérifiée : émulateur 1.0.156, dernière mise à jour du 29 octobre à 02:17:41 ; Mi Box 1.0.164, dernière mise à jour du 29 octobre à 22:20:04.

Les captures de cette section portent sur la redondance et le focus. Elles ne prouvent pas une amélioration chiffrée de fluidité ni une couverture de 100 % de tous les onglets. Les anomalies A36–A38 et les autres éléments ouverts des chapitres précédents restent à traiter. Aucun runner cloud, push, commit imposé ou publication de release.

## 19. Commandes de lecture libVLC — lot 245, 2 novembre 2026

**La touche Pause ne fonctionnait pas sur le lecteur libVLC mobile lorsque les commandes étaient masquées.** Le défaut a été reproduit en 244, corrigé en 245, puis le même scénario a été exécuté avec succès. Les tests utilisent la playlist existante, sans communiquer ses identifiants.

### A40 — P1 : touches média ignorées lorsque les commandes disparaissent

| Symptôme | Cause vérifiée | Fichier | Risque | Correction 245 | Validation |
|---|---|---|---|---|---|
| Après masquage des commandes, KEYCODE_MEDIA_PAUSE (127) laisse le bouton Pause affiché au retour et la lecture progresse | Le gestionnaire onPreviewKeyEvent dépendait du focus d’un enfant ; les commandes libVLC masquées suppriment leurs cibles de focus et la racine ne retenait pas le focus clavier | ui/vod/VodPlayerScreen.kt | Pause matérielle inopérante ; incohérence entre commandes visibles et masquées | Dans Next mobile avec libVLC actif, rendre la racine du lecteur focusable et lui redonner le focus après une frame lorsque les commandes se masquent. Réutiliser le gestionnaire du moteur actif ; ne pas déplacer les décodeurs ni changer de surface. Ajouter un journal de touche limité au code, au moteur, à la visibilité et à la position | Avant : Pause ignorée, deux relevés 01:42 puis 01:52. Après : pause stable à 01:22 ; Lecture relance le flux ; Lecture/Pause fonctionne commandes masquées ; avance/recul vérifiés à ±10 s |

Le focus est limité au package Next, au chemin mobile libVLC et à l’absence de diffusion Chromecast. Les contrôles visibles conservent leurs interactions. Le chemin Media3 TV, les dépendances et la persistance des profils ne sont pas modifiés. Les journaux ajoutés ne contiennent ni URL ni secret.

Les codes de touches sont envoyés par ADB au système Android. Il ne s’agit pas d’un test de casque Bluetooth, de commande vocale ou de téléphone physique ; ces chemins restent à vérifier séparément.

### Résultats réels sur le build 245

| Parcours | Résultat observé | Preuve et portée |
|---|---|---|
| Épisode S01E01 de ES - Ágata y Lola sur émulateur | Progression réelle 01:10 → 01:17 avec libVLC et image visible | Lecture du flux configuré ; pas une simple ouverture d’écran |
| Pause, commandes masquées | Deux relevés identiques 01:22 / 49:43, bouton Reprendre visible après réaffichage | Échec 244 conservé ; même scénario réussi 245 |
| Lecture, commandes visibles | 01:27 → 01:34, bouton Pause | KEYCODE_MEDIA_PLAY (126) atteint le moteur actif |
| Lecture/Pause, commandes masquées | Deux appuis sur 85 : lecture puis pause stable à 01:39 | Journaux VLC controlsVisible=false ; la touche ne déclenche pas un double basculement sur relâchement dans cet essai |
| Avance et recul, lecture en pause | 01:39 → 01:49 → 01:39 | Codes 90 et 89 réellement exécutés par libVLC ; position initiale retrouvée |
| Menu Audio | Piste Stereo - [Spanish] signalée sélectionnée ; option Disable également fournie par libVLC | Menu issu du lecteur réel, ouverture et fermeture testées. Pas de changement de piste ni d’écoute acoustique validés |
| Menu Sous-titres | Désactivés sélectionné ; Track 1 - [Spanish] disponible | Ouverture et fermeture testées ; activation et rendu des sous-titres non validés |
| VOD Mi Box au DPAD | Image réelle ; Media3 READY et playing=true ; pause par touche 127 ; deux checkpoints à 305227 ms espacés d’environ 15 s | Première image rapportée à 1920 × 924 ; captures à 05:01 puis 05:05. Aucune extrapolation à tous les codecs ou à la 4K |
| Journaux des deux processus testés | Aucun FATAL EXCEPTION ni JNI_ERR trouvé dans les journaux collectés | Contrôle limité à ces sessions ; pas une preuve d’absence de tout crash futur |

Les journaux émulateur indiquent notamment Media key=127 engine=VLC controlsVisible=false positionMs=82876, puis les événements 126, 85, 90 et 89 sur le moteur VLC. Sur Mi Box, le journal indique Media key=127 engine=Media3, puis READY / playing=false et deux positions identiques. Les extraits conservés dans les preuves excluent les URL.

### A38 : rognage finalement reproduit, correction visuelle toujours ouverte

Les premières captures sur S01E01 étaient lisibles. Après changement vers S01E02, ouverture/fermeture du dialogue Audio et répétition des pauses/reprises, certaines captures 244 montrent de nouveau le titre ou le temps partiellement rognés au bord gauche. Les positions d’accessibilité demeurent cohérentes : le titre est annoncé à [42,643][1016,706], les commandes à l’intérieur de l’écran. Cela distingue le rendu visible de la seule mesure du layout, sans établir encore sa cause racine.

La dépendance effective libVLC 3.7.6 et le chemin TextureView mobile ont été inspectés dans l’artefact local, sans changer de version ni de moteur. L’émulateur utilise un rendu EGL émulé et SkiaGL. Une interaction de composition reste une hypothèse ; elle ne constitue pas un diagnostic confirmé sur téléphone physique. Les captures de défaut sont conservées, et A38 n’est pas déclaré résolu par la correction de focus.

Les captures 245 incluses documentent les touches, l’image et les menus. Le test n’a pas repris tous les cycles de changement d’épisode après chaque dialogue sur 245 ; la résolution du rognage reste à démontrer. Certains libellés fournis par libVLC restent en anglais, ce qui demeure une amélioration de présentation.

### Compilation, installation et conservation des données

Build local Windows **1.0.245-next-perf / 2020102245**, package com.tvflow.app.next : **BUILD SUCCESSFUL en 4 min 36 s ; 172 tests unitaires sans échec ; lint zéro erreur, 36 avertissements et 7 indications ; git diff --check passe.** La version est relue sur l’émulateur et la Mi Box après installation. Les validations complémentaires sont des parcours sur appareils.

APK : G:\DevServer\audits\TVFlow-Next-20261102\lot245\TVFlow-v2-Next-1.0.245-perf.apk.
SHA-256 : **0B90D27A02C6595023F78BA7C05E5354D7A29613AD2D18B99EC0AF2862B8F205**.

Installation par mise à niveau, sans désinstallation ni effacement ; progression de lecture conservée. Le lecteur de l’émulateur est arrêté avant le test Mi Box, puis les sessions Next sont arrêtées à la fin. TVFlow original conserve les mêmes versions et dates : 1.0.156 / 29 octobre 02:17:41 sur émulateur ; 1.0.164 / 29 octobre 22:20:04 sur Mi Box.

Aucune publication, aucun runner cloud, aucun changement de clé ou de profil. L’accueil simplifié 244 est conservé. Les priorités ouvertes restent notamment A36 (vraie fin et interruptions réseau), A38 (composition), portrait/téléphone physique, import massif, Groq distant et validation complète des sauvegardes/mises à jour. Aucun score global de 100 % ni gain chiffré de fluidité n’est annoncé.

## 20. Portrait et adaptation des onglets — lots 246 à 248, 2 novembre 2026

**Le portrait est débloqué dans Next mobile, avec une présentation vérifiée sur les neuf onglets de l’émulateur.** Direct, Films, Séries et Guide utilisent toute la largeur pour leur contenu ; leurs catégories s’ouvrent dans un panneau temporaire en portrait et restent latérales en paysage et sur TV. La version finale installée sur les deux appareils est **1.0.248-next-perf / 2020102248**.

Cela valide des parcours précis, sans certifier toutes les fonctions de tous les onglets. Le téléphone physique, Groq distant, sauvegardes/mises à jour et les autres limites déjà documentées restent hors de cette validation.

### Causes vérifiées et corrections

| Anomalie | Symptôme et cause | Fichiers | Risque | Correction ou recommandation | Validation |
|---|---|---|---|---|---|
| A01 / A41 — P1, portrait bloqué | Malgré une rotation portrait demandée, le build 245 reste en 2400 × 1080 ; le manifeste Next impose sensorLandscape | app/build.gradle.kts ; MainActivity.kt ; AndroidManifest.xml consomme le placeholder | Téléphone inutilisable en portrait, orientation confondue avec une interface TV | Next utilise unspecified ; la TV conserve un verrouillage paysage explicite, ainsi que le chemin historique non Next | Avant : capture paysage malgré rotation 0. Après : neuf onglets à 1080 × 2400 ; Films passe en 2400 × 1080 puis retrouve le portrait |
| A41 — P2, panneaux trop larges | Direct réserve 310 dp aux catégories ; bibliothèques et Guide réservent aussi une colonne fixe | Nouveau ui/common/NextAdaptivePane.kt ; HomeScreen.kt ; MediaLibraryScreen.kt ; GuideScreen.kt | Trop peu de place pour chaînes, grilles et programmes sur téléphone | Panneau temporaire en portrait ; même contenu LazyColumn conservé ; fermeture après sélection. Largeurs EPG adaptées ; filtres et actions du panorama peuvent revenir à la ligne | Menus ouverts/fermés dans les quatre sections ; sélection réelle d’une catégorie Films ; grilles à trois colonnes et grille EPG visibles |
| A42 — P2, Recherche comprimée | Le titre et le bouton IA partagent une Row sans place suffisante ; le bouton se coupe sur plusieurs lignes en 246 | ui/search/SearchScreen.kt | Action peu lisible, résultats gênés par le clavier | En portrait : titre et bouton sur deux rangées, libellé IA sur une ligne, marges compactes et prise en compte des insets IME | Capture du défaut 246 conservée. En 247 puis 248, bouton lisible, clavier affiché et résultats réels consultables ; lien IA opérationnel |
| A43 — P2, ouvert | Titres sombres sur les cartes de suggestions Séries ; Text hérite sa couleur sans définition adaptée au fond bleu | ui/series/SeriesDetailsScreen.kt : SeriesSuggestionCard, notamment Text item.name | Contraste insuffisant pour lire les suggestions | Définir la couleur de contenu des cartes, puis harmoniser les cartes Films/Séries avec le style commun | Défaut visible sur la capture de retour du lecteur ; correction non appliquée dans ce lot |

La hauteur du panneau temporaire est calculée à partir de la fenêtre via LocalWindowInfo et LocalDensity. La première version utilisait Configuration.screenHeightDp, signalé par lint ; cette nouvelle alerte est corrigée dans 248. Le composant conserve une liste paresseuse ; les Repositories, les requêtes SQL et Paging ne sont pas modifiés. Aucun chargement intégral supplémentaire du catalogue n’est introduit pour construire ces interfaces.

Les trois actions du panorama sont conservées et peuvent revenir à la ligne. La barre de navigation unique du lot 244 reste en place ; son défilement horizontal est intentionnel sur téléphone. Les détails, profils, favoris et historiques ne sont pas remplacés par des maquettes.

### Matrice des parcours de ce lot

| Onglet | Portrait émulateur | Paysage téléphone/émulateur | Mi Box | Limites |
|---|---|---|---|---|
| Accueil | Panorama, actions et deux rails visibles ; état Dans ma liste conservé | Navigation après rotation, sans nouveau benchmark | Retour Accueil et dialogue Détails au DPAD en 246 ; Accueil 248 capturé | Tous les contenus du panorama non essayés |
| Direct | 25 chaînes TNT visibles ; catégories accessibles par panneau | Panneau latéral conservé par le layout | Lecture Live antérieure 243 conservée comme preuve historique | Pas de nouvelle lecture Live portrait dans ce lot |
| Guide TV | Grille EPG réellement remplie et catégories ouvertes/fermées | Pas de nouveau parcours complet paysage mobile | Grille 248 chargée après attente de 35 s ; passage vers Films ; PID 28429 conservé, aucun nouvel ANR pour ce PID | Un essai 248, pas une certification de stabilité générale |
| Films | Grille de trois affiches par rangée ; catégories et sélection testées | Rotation aller/retour, destination Films conservée | Bibliothèque 246/248 visible et accessible au DPAD | Tous les filtres et toutes les catégories non parcourus |
| Séries | Grille, fiche, saisons/épisodes et lecture d’un épisode accessibles | Le lecteur passe en paysage avec une image réelle, puis Retour retrouve la fiche en portrait | Preuves antérieures conservées | Contraste A43 ; toutes les saisons et pistes non testées |
| Recherche | Possession donne des résultats réels avec clavier visible ; bouton IA lisible en 248 | La requête Possession survit aux deux rotations | Recherche 248 ouverte au DPAD ; en-tête et retour Accueil inspectés | Pas de benchmark de recherche sur un import neuf de 100000 titres |
| IA | Page et raccourci depuis Recherche accessibles ; suggestions visibles | Couverture antérieure, pas de nouveau test de génération | Page historique conservée | Groq distant non validé |
| Favoris | Filtres visibles ; état vide de la rubrique Chaînes cohérent | Persistance vérifiée dans les lots précédents | Pas de nouveau cycle ajout/suppression dans ce lot | Pas de synchronisation inter-appareils déduite |
| Réglages | Page principale lisible ; sections accessibles visuellement | Pas de nouveau parcours des sous-pages | Couverture antérieure conservée | Comptes, sauvegarde, mise à jour et permissions non testés intégralement |

Le parcours portrait 246 a capturé chaque onglet. Il s’est arrêté ensuite parce que le script faisait défiler la barre dans le mauvais sens depuis Réglages pour retrouver Films. Le geste a été corrigé dans le script de suivi ; l’application n’a pas été modifiée pour contourner cet échec d’automatisation.

Sur Mi Box, le premier relevé d’accessibilité 246 a expiré. L’ancienne hiérarchie XML n’a pas été réutilisée. Les captures ont montré Films, puis les actions DPAD ont réellement ramené à Accueil et ouvert Détails. Les essais 248 complètent ces preuves avec Recherche et Guide vers Films ; un écran visible n’est pas compté à lui seul comme preuve de lecture vidéo.

### Rotation, clavier et données

- Avant correction, une demande portrait sur l’émulateur donnait une image 2400 × 1080 en 245.
- En 246, les neuf onglets produisent des captures 1080 × 2400 ; Films passe en paysage 2400 × 1080 puis revient au portrait sans changer de destination.
- Depuis la fiche Séries en portrait, un épisode s’ouvre en paysage avec une image décodée. Retour retrouve la fiche et sa liste d’épisodes en 1080 × 2400.
- En 247 puis 248, la recherche Possession affiche des résultats avec le clavier virtuel réellement visible ; la requête reste présente en paysage et au retour portrait.
- Le panneau de catégories final 248 s’ouvre correctement et Fermer le referme. La sélection d’une catégorie avait également été exécutée en 246.
- Les réglages système de rotation et de clavier modifiés pour les essais sont restaurés. Les sessions Next sont arrêtées à la fin.

Les captures Réglages destinées au rapport masquent automatiquement les éventuels champs éditables, URL et valeurs de type jeton. La page principale capturée ne contient pas d’identifiants de compte. Aucun profil IPTV n’a été réécrit ni supprimé.

### Validation et version finale

Build local final : **BUILD SUCCESSFUL en 2 min 43 s ; 172 tests unitaires sans échec ; lint zéro erreur, 37 avertissements et 7 indications ; git diff --check passe.** Les 37 avertissements restants ne sont pas masqués. L’alerte ConfigurationScreenWidthHeight ajoutée avec le premier panneau a été supprimée par le calcul basé sur la fenêtre ; les captures 246 et 247 restent des preuves intermédiaires datées.

APK final : G:\DevServer\audits\TVFlow-Next-20261102\lot246\TVFlow-v2-Next-1.0.248-perf.apk.
SHA-256 : **620D3A2EF25C5E5A26107A938A958C0248097C616C1527A758141273AA9C4686**.

Versions installées relues : 1.0.248-next-perf / 2020102248 sur émulateur et Mi Box. Les tests ciblés finaux de Recherche, clavier, rotations et navigation TV ne montrent pas de FATAL EXCEPTION ni JNI_ERR dans les journaux de leurs processus. Le Guide 248 reste dans le même processus pendant le scénario et ne produit pas de nouvel événement ANR détecté ; les anciennes traces d’échec restent conservées.

TVFlow original garde 1.0.156 / 29 octobre 02:17:41 sur émulateur et 1.0.164 / 29 octobre 22:20:04 sur Mi Box. Aucun effacement, aucune migration de base, aucune publication, aucun runner cloud ni refonte de l’architecture des données.

### Priorités restantes

P1 : imports massifs, interruptions réseau/vraie fin d’épisode, couverture complète des lectures et des sauvegardes/mises à jour. P2 : rognage libVLC A38, contraste A43, cohérence des panoramas, navigation et accessibilité sur téléphone physique, génération Groq distante et mesures comparables de fluidité/mémoire. Les neuf onglets sont désormais affichables en portrait sur l’émulateur, mais l’objectif fonctionnel global de 100 % reste à démontrer.


## 21. Suggestions Séries et lecture Live mobile — lots 249–250, 2 novembre 2026

### Diagnostic vérifié

| Anomalie | Symptôme → cause | Fichiers | Risque | Correction | Test associé |
|---|---|---|---|---|---|
| A43 — P2 | Titres noirs sur cartes bleu sombre des suggestions Séries. Le fond est personnalisé mais Text hérite de LocalContentColor ; la couleur du thème n’est pas résolue pour ce fond arbitraire | ui/series/SeriesDetailsScreen.kt, SeriesSuggestionCard | Suggestions difficiles à lire | Next : blanc au repos, onPrimaryContainer au focus. Le chemin historique conserve sa couleur héritée | Comparaison réelle avant 248 / après 249 ; portrait, paysage et focus Mi Box. Une suggestion ouvre sa propre fiche, au toucher et au DPAD |
| A44 — P2 | Le plein écran Live reste en 1080 × 2400 après ouverture depuis Direct portrait, avec une vidéo horizontale au milieu de grandes bandes noires. PlayerScreen ne demande aucune orientation ; VodPlayerScreen demandait déjà le paysage | ui/player/PlayerScreen.kt, DisposableEffect(activity) | Surface vidéo trop petite et expérience incohérente entre lecteurs | Next mobile : SENSOR_LANDSCAPE durant la lecture ; restauration de l’orientation précédente à la sortie. Garde explicite pour préserver TV et application historique | Échec réel 249 conservé ; scénario 250 : premier/deuxième appui, aperçu, plein écran, retour portrait et inspection des images/journaux |

La carte de suggestion Films utilise surfaceVariant/onSurfaceVariant et primaryContainer/onPrimaryContainer du thème. Elle ne présente pas la même cause liée à un fond arbitraire ; aucun changement spéculatif ne lui est appliqué. Les requêtes de suggestions bornées, Paging, les profils et les favoris ne sont pas modifiés.

### Résultats du build 249

- Compilation locale : BUILD SUCCESSFUL en 4 min 18 s ; 172 tests unitaires, zéro échec ; lint zéro erreur, 37 avertissements et 7 indications.
- Installation de com.tvflow.app.next 1.0.249-next-perf / 2020102249 sur émulateur et Mi Box par mise à jour conservant les données.
- Portrait : fiche Ágata, suggestions lisibles, ouverture de Agente Kim et retour ; paysage : suggestions lisibles après rotation. Captures réellement inspectées.
- Mi Box : navigation exclusivement au DPAD vers Ágata, saison et cinq épisodes, focus sur Alice y Steve, ouverture de sa fiche puis Retour. Aucun FATAL EXCEPTION ou JNI_ERR dans les journaux du processus 31757 pendant ce parcours.
- Direct avec le profil déjà configuré : TF1 réellement décodée dans l’aperçu. Au premier appui, la liste reste sans lecteur ; une capture prise après le deuxième appui et une courte attente reste sans image, puis l’aperçu affiche une image réelle après attente supplémentaire. Ce relevé ne mesure pas le délai à la milliseconde ; le délai de 2000 ms est également couvert par le code et les tests existants.
- Le test du plein écran échoue en 249 sur la géométrie attendue : la capture reste portrait. Cet échec et sa trace ne sont pas effacés ni comptés comme un succès. L’image contient une vidéo décodée, mais le rendu utilise mal la surface disponible.

Les assertions d’ouverture de fiche sont complétées par l’inspection de captures montrant le nouveau titre et ses propres épisodes ; une simple présence du titre dans une ancienne rangée de suggestions ne suffirait pas.

### Validation finale 250

**1.0.250-next-perf / 2020102250** est installé sur les deux appareils. Compilation locale : **BUILD SUCCESSFUL en 2 min 21 s ; 172 tests unitaires, zéro échec ; lint zéro erreur, 37 avertissements et 7 indications ; git diff --check passe.**

Sur l’émulateur, le scénario Direct atteint le plein écran en **2400 × 1080**, avec deux images réellement décodées espacées de cinq secondes et un mouvement visible de la présentatrice. Les journaux du processus 26826 contiennent 26 lignes liées à VLC, aucune erreur Java fatale ni JNI_ERR. Aucun événement de première image Media3 n’est revendiqué pour cette lecture : le lecteur mobile utilise libVLC. Les métriques détaillées TVFlowVlc sont réservées au mode DEBUG ; leur absence dans Perf n’est pas assimilée à un échec de décodage. Les images et les journaux sont considérés ensemble.

Retour restaure **1080 × 2400**, la destination Direct et sa liste TNT de 25 chaînes. Les réglages de rotation système utilisés par le test sont restaurés ; aucun favori n’est modifié. Le premier contrôle Mi Box 250 s’arrête lors de la demande de hiérarchie d’accessibilité après le cinquième épisode : l’outil ne peut obtenir un XML frais. L’ancien XML n’est jamais réutilisé. Aucun événement ANR Next n’apparaît dans le relevé système consulté immédiatement ensuite. Cela ne suffit pas à certifier une absence générale de blocage ; un second parcours au DPAD avec captures est exécuté séparément et son résultat est consigné ci-dessous.

APK local : G:\DevServer\audits\TVFlow-Next-20261102\lot249\TVFlow-v2-Next-1.0.250-perf.apk.
SHA-256 : **6C86932B969CFE7274D86167A2F304944E8B8C6C7BADC16DA94CEC65CA7F11EB**.

Le second parcours Mi Box 250, indépendant de l’extraction XML, est terminé : navigation DPAD vers les suggestions, focus visible sur Alice y Steve, ouverture de sa fiche puis Retour. Les captures 1920 × 1080 montrent les états attendus. Le PID **382** reste identique pendant ce scénario ; aucun FATAL EXCEPTION, JNI_ERR ou événement ANR pour ce PID n’est relevé. Ce résultat complète l’essai 249 réussi, tout en conservant l’échec d’automatisation 250 ci-dessus.

| Environnement | Résultat de ce lot | Limite |
|---|---|---|
| Émulateur portrait | Suggestions lisibles, ouverture de fiche ; Direct sélection/aperçu puis retour portrait | Pas de téléphone physique |
| Émulateur paysage | Suggestions lisibles ; lecture Live 250 avec images successives | Pas de validation exhaustive des codecs, pistes ou chaînes |
| Mi Box | Suggestions, focus, ouverture et Retour au DPAD en 249 et captures de contrôle 250 | Pas de nouvelle lecture vidéo TV dans ce lot ; preuves antérieures conservées |
| Téléphone physique | Non testé, absent des appareils ADB | Aucune équivalence de performance avec l’émulateur revendiquée |

### Mesures de mémoire ponctuelles

| Appareil et parcours actif | PSS total | Autres mesures | Portée |
|---|---|---|---|
| Émulateur Android 15, lecture Live 250 | 335788 KiB, soit environ 328 Mio | RSS 468816 KiB ; heap natif PSS 225236 KiB ; Java PSS 51760 KiB | Un relevé dumpsys meminfo pendant le scénario, sans mesure du pic ni comparaison avant/après |
| Mi Box Android 9, fiche Séries et navigation DPAD 250 | 58349 KiB, soit environ 57 Mio | heap natif PSS 17376 KiB ; Java PSS 12820 KiB | Écran différent du lecteur mobile : les deux valeurs ne constituent pas un benchmark comparatif |

Ces relevés ne valident ni l’import de 100000 films ni l’absence de fuite/OOM sur une longue session. Aucun nouveau chiffre de fluidité ou de temps de recherche n’est déduit de la réussite des parcours.

### Périmètre préservé et priorités

Travail exclusivement dans le worktree Next, branche feature/tvflow-next-cinematic-ui vérifiée. Les modifications non commitées antérieures sont conservées. TVFlow original garde les versions 1.0.156 et 1.0.164 et leurs dates de mise à jour précédentes. Aucun effacement de données, aucune migration, aucun commit/push, aucune publication ni runner cloud.

A43 est corrigé et visuellement vérifié sur trois dispositions ; A44 est corrigé et validé sur le parcours Live mobile décrit. Le téléphone physique n’est toujours pas connecté. Les autres codecs/chaînes, la qualité audio entendue, le rendu des sous-titres, les coupures réseau, la fin naturelle des épisodes, le rognage A38, les imports massifs, Groq distant et les sauvegardes/mises à jour restent à compléter. Le niveau global de 100 % n’est pas déclaré.


## 22. Tests sur Galaxy A54 physique — 2 novembre 2026

Le Galaxy A54 SM-A546B est maintenant connecté. Les résultats de cette section concernent le téléphone réel, distinct de l’émulateur et de la Mi Box. Le build installé reste **1.0.250-next-perf / 2020102250**, testé sans nouvelle modification du code de l’application ni recompilation.

Android **16 / API 36**. Écran natif : **1080 × 2340**, densité 450 dpi. Les profils déjà présents sur ce téléphone sont utilisés ; la catégorie Films sélectionnée est VOD - FRENCH 4K et la catégorie Séries SRS - SERIES 2026 [FR]. Aucune configuration IPTV de l’émulateur n’est copiée sur le téléphone. Les titres « 4K » des catégories/chaînes ne constituent pas une mesure de résolution décodée.

### Parcours d’interface

Les neuf onglets ont été ouverts et capturés en portrait. Direct montre 25 chaînes TNT ; le Guide affiche une grille EPG réellement remplie. Les panneaux de catégories de Direct, Guide, Films et Séries ont été ouverts puis refermés, sans changer la catégorie ni les favoris. Films et Séries présentent trois colonnes d’affiches. IA, Favoris et Réglages sont accessibles ; cette observation ne certifie ni une génération Groq distante, ni les sauvegardes et mises à jour.

Recherche : la requête **possession** affiche de vrais résultats du catalogue avec le clavier Samsung visible. La même requête est conservée après passage en paysage puis retour portrait. Le contrôle du processus 10386 ne relève ni FATAL EXCEPTION, ni JNI_ERR, ni événement ANR pour ce PID pendant le scénario.

Les captures révèlent plusieurs particularités du téléphone à distinguer d’une panne de l’application :

- Le premier script exigeait une largeur de texte supérieure à 60 pixels pour sélectionner un onglet ; cela excluait le court libellé IA. Le seuil du script est adapté à la longueur du libellé, sans modifier l’application.
- La première injection de Possession avait produit **ossession avant toute rotation**. Cette même chaîne est bien conservée en paysage. L’assertion comparait à tort avec la chaîne souhaitée ; les captures d’échec sont conservées. Le nouvel essai attend le clavier, efface le champ et injecte possession, puis compare la valeur réellement présente avant/après : réussite.
- Le clavier Samsung se réaffiche lors de la rotation d’un champ focalisé et masque temporairement la navigation en paysage. Le premier tour paysage s’arrête avant Accueil. Le parcours suivant ferme explicitement le clavier avant de tester la navigation ; aucun XML ancien n’est réutilisé.

Le démarrage d’activité mesuré par am start -W après force-stop donne **379 ms**. Le script attend ensuite le contenu avant ses assertions. Ce chiffre mesure l’ouverture de l’activité dans ces conditions, avec les caches existants ; il ne mesure pas le chargement complet d’un catalogue neuf et ne justifie pas un temps de démarrage global de 379 ms.

### Résultats complémentaires

Les neuf onglets ont également été ouverts et capturés en **2340 × 1080**. Le processus 10386 reste vivant pendant les parcours de navigation, sans erreur Java fatale, JNI_ERR ou événement ANR détecté pour ce PID.

Le Direct est testé sur TF1 avec la configuration déjà présente : premier appui sans lecture, second appui puis attente avant un aperçu avec image réelle, troisième appui vers le plein écran paysage. Deux captures espacées de cinq secondes montrent des images différentes. Le retour retrouve Direct en portrait. Les journaux du processus contiennent 16 lignes liées à VLC, sans événement de première image Media3 revendiqué pour cette lecture mobile.

**A38 est aussi observé sur le téléphone physique** : le titre et les métadonnées du lecteur Live, entièrement visibles sur une première capture, perdent une partie de leurs caractères à gauche cinq secondes plus tard. La vidéo continue à changer. Il ne s’agit donc plus d’un défaut observé uniquement sur l’émulateur ; ce lot ne démontre pas encore la cause du rognage et n’applique pas de changement spéculatif de surface ou de décodeur.

### Fluidité et mémoire — relevé physique

Après remise à zéro des compteurs gfxinfo de Next, huit gestes de défilement dans Films prennent 6,36 secondes de commandes et d’attentes du script. Les compteurs relevés ensuite donnent **783 images rendues, 12 janky frames (1,53 %), médiane 10 ms, P90 11 ms, P95 12 ms, P99 13 ms**. Ce court essai utilise le catalogue et les caches existants ; il ne mesure pas un import neuf de 100000 titres, ni toutes les conditions thermiques/réseau ou les longues sessions. Les pourcentages ne sont pas convertis en certification de fluidité générale.

À la fin de ce scénario Films : PSS **205979 KiB (environ 201 Mio)**, RSS 308698 KiB, heap natif PSS 64500 KiB, Java PSS 22512 KiB. Il s’agit d’un instantané, pas d’un pic mémoire ni d’un test de fuite.

### Points visuels à corriger ou poursuivre

| Anomalie | Symptôme → cause vérifiée ou limite du diagnostic | Fichiers | Risque et priorité | Correction recommandée | Validation à prévoir |
|---|---|---|---|---|---|
| A38 — confirmation téléphone | Rognage intermittent de textes du lecteur Live malgré une vidéo qui avance ; cause racine non démontrée par ce test | ui/player/PlayerScreen.kt ; ui/common/VlcFallbackPlayer.kt | P2, commandes et informations moins lisibles | Isoler le rendu des surimpressions et le cycle de vie de la surface, avec captures et traces de rendu ; ne pas remplacer le moteur sans preuve | Captures répétées avec et sans contrôles sur A54 et émulateur, puis comparaison des moteurs et de la fluidité |
| A45 — paysage Films/Séries | Le haut de page fixe consomme une grande partie de la faible hauteur disponible ; les cartes compactes gardent un ratio portrait 2/3 et leurs titres sont hors de la zone initialement visible | ui/library/MediaLibraryScreen.kt, compactMobile = !isTv, en-tête et PosterCard | P2, affiches/titres difficiles à consulter ensemble en paysage mobile | Adapter la hauteur du bandeau et la taille des cartes à la hauteur de fenêtre, ou rendre l’en-tête rétractable | Première rangée avec affiche et titre consultables en 2340 × 1080, défilement et catégories toujours accessibles |
| A46 — consigne TV sur tactile | Le Guide indique « OK : aperçu / OK encore : plein écran » sur le téléphone ; le texte est codé en dur | ui/guide/GuideScreen.kt : texte du bandeau vers ligne 464 | P3, consigne ambiguë au toucher et par rapport au premier appui de sélection | Adapter la consigne au mode tactile/DPAD et à la séquence de sélection puis aperçu temporisé | Lecture de la consigne et parcours réel au toucher et au DPAD |
| A47 — fiche Film portrait | Le troisième bouton Retour est hors de la largeur visible, derrière Lecture et Favoris ; les trois boutons sont dans une Row sans retour à la ligne à côté de l’affiche | ui/vod/VodDetailsScreen.kt : Row des actions de la fiche | P2, action Retour de la fiche invisible, malgré le Retour système disponible | Actions sur une rangée adaptative ou sous le bloc affiche/texte en portrait | Capture A54 portrait avec les trois actions visibles et utilisables, puis rotation et retour du lecteur |

### Lecture VOD et épisode

Film utilisé : **FR - Projet Dernière Chance (2026)**. Fiche portrait puis lecteur paysage. La première capture vidéo est noire et n’est pas retenue comme preuve de lecture réussie ; les captures suivantes montrent des images et la progression du lecteur.

Pause par commande média : **27 s → 27 s** après cinq secondes d’attente. Avance : **27 s → 37 s** ; recul : **37 s → 27 s**. Les dialogues affichent notamment des pistes audio French/English et des pistes de sous-titres English/French. Seule l’ouverture et la présentation des listes sont validées ; ni l’écoute effective, ni le changement de piste et le rendu de sous-titres ne sont déduits de ces captures. Le code utilise une LazyColumn dans le dialogue : le bas d’une liste partiellement visible n’est pas assimilé à une absence de défilement.

Le premier relevé automatique de position s’était arrêté parce que les contrôles masqués ne fournissaient aucun texte d’accessibilité. Le scénario de suivi révèle d’abord les contrôles puis mesure les positions ; l’échec initial reste conservé. Aucun ancien XML n’est utilisé.

Après relance de la lecture puis une nouvelle pause de mesure, la position passe de **206 à 211 secondes** pendant cinq secondes de lecture commandée : reprise effective confirmée. Le contrôle du processus est sain sur ce scénario.

Le premier contrôle du retour Film a produit une capture paysage de la fiche, et son assertion portrait a échoué. Le contrôle du protocole montre toutefois que les scénarios de suivi avaient déjà rétabli accelerometer_rotation=1 pendant que le lecteur restait ouvert. Le capteur du téléphone pouvait donc imposer le paysage à la sortie. Ce constat invalide l’attribution immédiate d’une anomalie à l’application : aucun A48 n’est retenu à ce stade. Un essai indépendant maintient le verrouillage portrait jusqu’après le retour, avant toute restauration des réglages utilisateur. Les traces et captures intermédiaires restent conservées.

L’extraction d’accessibilité pendant la lecture en mouvement peut terminer après le masquage automatique des contrôles. Le relevé de suivi met en pause avant la lecture de la position, ce qui stabilise l’interface ; cela permet de distinguer une limite d’automatisation d’un échec de la commande Play.

La reprise mémorisée du film est validée : **211 s enregistrées → 220 s après réouverture et chargement**. Les journaux du processus contrôlé restent sans erreur Java fatale, JNI_ERR ou événement ANR. L’épisode de **FR - Les Disparus des Argonnes (2026)** démarre depuis une fiche portrait et se lit en paysage, avec deux images réelles différentes et sans erreur fatale/JNI/ANR relevée pour le processus 23236.

Le bouton Épisode suivant est trouvé et activé ; une nouvelle image vidéo est capturée. L’identification exacte du nouvel épisode n’est toutefois pas validée : les contrôles disparaissent avant le relevé d’accessibilité, et l’essai de suivi ne peut pas lire une position/titre fiable. Cette action n’est donc pas présentée comme un changement d’épisode complètement réussi.

Un contrôle de sortie indépendant force accelerometer_rotation=0 et user_rotation=0 pendant toute la mesure. Après Retour, la capture montre la **fiche Séries encore en 2340 × 1080**. La restauration du portrait échoue donc réellement sur ce parcours avec action Épisode suivant, contrairement au premier essai Film non contrôlé. Ce cas est retenu séparément comme A48 ; la cause exacte reste à tracer. Le nouvel essai Film, depuis une activité fraîche et avec rotation contrôlée jusqu’après Retour, **réussit** : fiche 1080 × 2340 → lecteur 2340 × 1080 → fiche 1080 × 2340. Les réglages relevés pendant le retour sont accelerometer_rotation=0 et user_rotation=0. Le processus 25121 reste sain sur ce parcours. A48 est donc limité au parcours Séries avec action suivante observé, et ne doit pas être présenté comme un échec général du retour VOD.


### Anomalie de retour depuis le parcours épisode

| Anomalie | Symptôme et preuve | Fichiers à inspecter | Priorité et correction recommandée | Validation |
|---|---|---|---|---|
| A48 — retour Séries après action Épisode suivant | La fiche de départ est portrait ; après lecture, action suivante et Retour, la fiche est paysage malgré un verrouillage système portrait maintenu pendant le relevé | ui/vod/VodPlayerScreen.kt : effet d’orientation ; ui/navigation/TVFlowNavHost.kt : remplacement du lecteur lors d’un autre épisode | P2 ; tracer les valeurs d’orientation et la durée de vie des effets entre lecteurs. Préserver une orientation initiale stable pour toute la session, après confirmation de la cause | Départ portrait, lecture et action suivante, Retour à la fiche portrait, répétition sur A54 et émulateur ; éviter de confondre un retour dans un lecteur et un retour dans la fiche |

### Bilan et limites

Les favoris, comptes et réglages applicatifs n’ont pas été édités. Les lectures de test mettent normalement à jour l’historique et les points de reprise ; aucun historique existant n’est effacé. Les réglages système de rotation sont restaurés après chaque scénario. L’application originale n’a pas été installée, ouverte ni effacée pendant ces tests.

Aucune nouvelle compilation n’était nécessaire : le code de l’application et le build 250 sont inchangés ; les 172 tests unitaires et contrôles lint documentés dans la section précédente restent les validations de ce build, sans être présentés comme réexécutés pendant ce lot.

Non testés intégralement : génération Groq distante, écoute et changement effectif des pistes, rendu des sous-titres, résolution 4K vérifiée par le décodeur, coupures réseau et reprise après erreur, fin naturelle d’un épisode, import massif neuf, sauvegardes et installation d’une mise à jour depuis l’application. Aucun 100 % global n’est déclaré. Aucune publication de release.


### Matrice de validation du téléphone

| Parcours | Résultat | Réserve |
|---|---|---|
| Neuf onglets portrait et paysage | Accessibles, captures inspectées | A45 sur la hauteur des grilles ; ne certifie pas toutes les fonctions des sous-pages |
| Catégories Direct/Guide/Films/Séries | Ouverture et fermeture réussies en portrait | Pas de parcours exhaustif de toutes les catégories |
| Recherche et clavier Samsung | Résultats réels, requête conservée aux deux rotations | Réglages de focus/IME pris en compte dans le script |
| Live TF1 | Aperçu, images successives, plein écran et retour portrait réussis | Rognage intermittent A38 ; son entendu et résolution 4K non certifiés |
| Film Projet Dernière Chance | Lecture réelle, pause stable, ±10 s, Play, reprise mémorisée, retour portrait contrôlé réussis | Menus de pistes ouverts ; sélection/écoute/rendu non validés ; A47 sur le bouton de la fiche |
| Épisode Les Disparus des Argonnes | Lecture réelle en paysage réussie | Action suivante exécutée mais identité du nouvel épisode non certifiée ; retour fiche portrait en échec après ce parcours, A48 |
| Réglages avancés, sauvegardes, mise à jour et Groq distant | Non testés intégralement | Ne pas déduire leur fonctionnement de la seule page principale |

Le téléphone est laissé sur l’accueil Next à la fin. Les défauts confirmés A38, A45, A46, A47 et A48 restent ouverts dans ce lot de tests ; aucune correction de l’application n’a été présentée comme appliquée.


## 23. Recherche et catégories masquées — build 251, 3 novembre 2026

**Défaut signalé par l’utilisateur : les résultats de recherche incluent des catégories masquées.** Le dépôt et la branche Next sont vérifiés avant intervention ; les changements antérieurs sont préservés.

### Cause racine et correction

| Anomalie | Symptôme → cause | Fichiers | Priorité et correction | Validation |
|---|---|---|---|---|
| A49 — catégories masquées recherchées | La recherche globale classe des couples nom/id sans catégorie ni préférence de visibilité. Les requêtes Films et Séries font LIKE + LIMIT sans lire les catégories masquées du DataStore. Le chemin global Live hydrate les résultats sans vérifier le masquage/verrouillage de catégorie | data/local/TVFlowDao.kt ; data/repository/XtreamRepository.kt ; ui/search/SearchViewModel.kt ; nouveau data/repository/SearchVisibility.kt | P1. Exclure avant le classement top-K / LIMIT ; respecter les clés VOD:id et SERIES:id par profil ; revérifier lors de l’hydratation ; faire réagir la recherche aux changements de visibilité | Six tests unitaires nouveaux ; deux tests Room réels sur Android ; cycle visuel avec index déjà chargé détaillé ci-dessous |

L’index compact conserve désormais la catégorie avec le nom normalisé et l’identifiant. Une photographie légère des préférences du profil courant est relue pour chaque recherche : catégories Films/Séries masquées, catégories Live masquées ou verrouillées et chaînes masquées individuellement. Le filtrage précède la limite : 500 correspondances masquées ne doivent pas empêcher une correspondance visible d’apparaître.

Un masquage puis réaffichage est pris en compte avec le même index en mémoire. La recherche globale réagit aussi aux changements de visibilité et de profil, même si le texte recherché ne change pas. Les flux de visibilité dédoublonnent les états égaux pour ne pas relancer la recherche à chaque modification sans rapport d’une table.

Les recherches des onglets Films/Séries passent les catégories exclues à Room, avant LIMIT. Les deux chemins locaux de candidats IA (catégories et genres TMDB), ainsi que les appels IA à la recherche globale, respectent ces exclusions. Une génération Groq distante n’est pas présentée comme retestée dans ce lot.

Aucune migration Room, suppression de catalogue, modification des favoris ou des comptes n’est ajoutée. Les entités complètes sont toujours hydratées seulement pour les identifiants retenus ; le classement garde la normalisation des accents et son ordre déterministe.

### Compilation et tests

- Build final local : **BUILD SUCCESSFUL en 36 secondes**, après une première passe de 5 min 21 s arrêtée par un paramètre manquant dans une donnée de test Android. Cette donnée a été corrigée ; les journaux des deux passes sont conservés.
- **178 tests unitaires sans échec**, dont six nouveaux : limite avant/après masquage, index déjà chargé, réaffichage, séparation VOD/Séries, chaînes masquées, classement et limites.
- **Deux tests Android Room réussis** sur émulateur, avec bases en mémoire indépendantes du catalogue utilisateur : exclusion avant LIMIT avec 200 éléments masqués par type ; liste d’exclusion vide ; hydratation ; isolation de profils partageant un identifiant de catégorie ; masquage/verrouillage Live puis réaffichage.
- Lint : **zéro erreur, 37 avertissements et 7 indications**, non masqués. git diff --check passe.

Les tests instrumentés sont exécutés manuellement sur le build Debug Next, installé avec conservation des données, puis le build Perf 251 est réinstallé pour la validation visuelle. Aucune tâche connectedAndroidTest susceptible de désinstaller l’application cible n’est utilisée.

APK : G:\DevServer\audits\TVFlow-Next-20261103\lot251\TVFlow-v2-Next-1.0.251-perf.apk.
SHA-256 : **0228E9CB1277CA830188E07368634CCF281A104B8B125E6D32A73769545A0CBF**.
Paquet : com.tvflow.app.next ; version **1.0.251-next-perf / 2020102251**.

### Contrôle visuel

Sur le build Perf 251 de l’émulateur, la requête Projet Derniere Chance montre d’abord deux occurrences du même film provenant de deux catégories visibles. Après masquage de VOD - RÉCEMMENT AJOUTER [FR] via les réglages officiels, la même recherche avec index déjà chargé ne montre plus qu’une occurrence : la catégorie encore visible reste recherchable. Après réaffichage, les deux occurrences sont revenues avec le même index et sans redémarrage. La catégorie testée est confirmée Visible à nouveau. Le processus 4021 reste sain : aucune erreur Java fatale, JNI_ERR ou événement ANR détecté pendant le contrôle. L’application est ramenée à Accueil ; les réglages de rotation de l’émulateur sont remis à leur état antérieur.

Le premier script de retour aux catégories a cherché la catégorie masquée dans le filtre Visibles, où elle est correctement absente. Son erreur de recherche et sa tentative de nettoyage sont conservées. Le suivi sélectionne explicitement Toutes puis remonte la liste, dont le défilement avait été conservé en fin de catalogue, avant de retrouver la catégorie ; aucun ancien XML ni accès direct aux préférences n’est utilisé pour contourner l’interface.

Le Galaxy A54 n’est actuellement pas connecté à ADB ; aucune installation du 251 sur ce téléphone n’est revendiquée. La Mi Box n’est pas sollicitée dans ce lot. Les profils, favoris et historiques sont conservés.


Le contrôle porte sur la recherche globale en interface, avec les recherches SQL Films/Séries et Live validées séparément dans Room. Les parcours complets de génération IA et une installation physique sur Galaxy A54 ne sont pas comptés comme réussis dans ce lot. L’APK de TVFlow original sur l’émulateur reste en 1.0.156, dernière mise à jour du 29 octobre à 02:17:41. Aucun commit/push, aucune release ni runner cloud.

Autre constat visuel, indépendant du correctif de recherche : **A50 — P2**, le bouton Réglages de l’organisation Films/Séries se comprime verticalement en portrait. SettingsScreen.kt place le long titre et le bouton dans une Row sans adaptation suffisante à la largeur. Prévoir un titre pondéré et des actions sur une ligne séparée en portrait ; valider les libellés et le retour sur A54/émulateur. Ce composant n’est pas modifié dans le build 251.


## 24. Disposition, cohérence visuelle et orientation — builds 252–255, 3 novembre 2026

Ce lot traite les défauts déjà observés dans les fiches, bibliothèques et réglages. Les captures avant 251 et après 252 sont prises sur le même émulateur avec le même catalogue. Les modifications ne remplacent aucune fonction par une maquette.

### Corrections et justification

| Anomalie | Cause et changement | Fichiers | Test de validation |
|---|---|---|---|
| A47/P2 — actions Film hors cadre | Trois boutons étaient contraints dans la colonne située à côté de l’affiche. Dans Next mobile portrait, les actions passent sous le bloc affiche/texte, sur une rangée pouvant revenir à la ligne. Le contenu de la fiche devient défilable sur mobile. Le titre est plus lisible et Ma liste reprend le vocabulaire de l’accueil | ui/vod/VodDetailsScreen.kt | Lecture, Ma liste et Retour visibles ; Retour réellement utilisé ; consultation en portrait/paysage et accès aux suggestions |
| A45/P2 — grille paysage trop basse | Le titre et le bouton de recherche occupaient deux rangées, et les cartes restaient trop larges pour la faible hauteur disponible. Next mobile paysage utilise une barre titre/recherche commune et des cartes adaptatives plus étroites, tout en conservant leur ratio d’affiche | ui/library/MediaLibraryScreen.kt | Captures Films et Séries en 2400 × 1080, première rangée et métadonnées inspectées ; portrait inchangé dans ce lot |
| A50/P2 — retour Organisation vertical | Le titre non contraint comprimait le bouton. Un en-tête réutilisable pondère la largeur du titre et réserve une action Retour sur une ligne, avec une surface arrondie et une bordure discrète. Les actions de sélection multiple peuvent revenir à la ligne sur mobile | ui/settings/SettingsScreen.kt, OrganizationHeader ; NextWrappingRow existant | En-têtes Films/Séries et Chaînes, boutons de groupe visibles, Retour ferme le panneau ; aucune sélection de catégorie ni action de masquage pendant ce parcours |
| A46/P2 — consigne de télécommande sur tactile | Le texte codé en dur ne décrivait pas les deux appuis. La consigne Next distingue le tactile et OK, avec le délai de deux secondes | ui/guide/GuideScreen.kt | Guide chargé et consigne tactile inspectée ; logique de lecture inchangée |
| A48/P1 — orientation entre épisodes | Les effets d’orientation étaient attachés séparément à chaque lecteur, remplacé au changement d’épisode. Next mobile possède désormais un seul propriétaire au niveau de la navigation, avec orientation initiale sauvegardée lors des recréations et conservée entre lecteurs. Les anciens effets d’orientation sont désactivés pour Next mobile ; leurs autres responsabilités sont conservées | nouveau ui/common/NextPlaybackOrientation.kt ; TVFlowNavHost.kt ; VodPlayerScreen.kt ; PlayerScreen.kt | Entrée depuis portrait, lecture, commande épisode suivant, Retour vers la fiche et inspection des événements d’orientation. Revalidation physique A54 nécessaire |
| A51/P1 — erreur de lecture après épisode suivant | Deuxième épisode identifié mais image noire. onError VLC publiait une erreur terminale alors que Media3 était préparé, retirant sa surface vidéo. Correction Next dans 255 : erreur fatale uniquement si le moteur de secours échoue. La cause initiale de l’échec VLC reste ouverte | ui/vod/VodPlayerScreen.kt ; VodPlayerViewModel ; ui/common/VlcFallbackPlayer.kt, à investiguer | Rejouer E2 directement depuis la fiche puis via E1 ; capturer code HTTP ou erreur moteur sans URL ; exiger une première image décodée, une progression et un retour fonctionnel |


La centralisation de l’orientation est limitée à cette responsabilité, afin d’éviter que le lecteur sortant restaure l’orientation pendant l’entrée du suivant. Elle ne remplace pas les ViewModels, Repositories, moteurs vidéo ou routes. Les journaux ajoutés ne contiennent que des valeurs d’orientation, aucun titre de flux, URL ou identifiant IPTV.

Les styles réutilisent la palette et les composants Compose existants : bleu profond, bordure fine, angles arrondis, actions lisibles. Aucun fond flouté plein écran ni nouvel effet permanent coûteux n’est ajouté. Les changements de densité de grille ciblent Next mobile paysage ; les catégories, Paging et le catalogue ne sont pas modifiés.

### Build et contrôles

- Build 252 : **BUILD SUCCESSFUL en 2 min 23 s** ; build 253 : **BUILD SUCCESSFUL en 2 min 15 s** ; build 254 : **BUILD SUCCESSFUL en 2 min 9 s** ; build final 255 : **BUILD SUCCESSFUL en 1 min 22 s**. Une première passe de 17 s avait signalé une référence Alignment manquante dans la nouvelle barre ; elle est corrigée et sa trace conservée.
- **178 tests unitaires réussis, zéro échec** ; lint zéro erreur, **37 avertissements et 7 indications** ; git diff --check passe.
- Les tests Room de visibilité du lot 251 restent documentés comme antérieurs ; ils ne sont pas présentés comme réexécutés dans ce lot d’interface.
- APK : G:\DevServer\audits\TVFlow-Next-20261103\lot252\TVFlow-v2-Next-1.0.255-perf.apk ; com.tvflow.app.next ; **1.0.255-next-perf / 2020102255**.
- SHA-256 : **F2F90547C1F7BDFEC3ECC7439703E4F54F247988735A8797FA3F0E75C11E0869**.

### Parcours réels et limites

Les dix contrôles d’interface ciblés sont terminés sur émulateur : Retour des deux panneaux Organisation, actions de sélection multiple affichées sans sélection de données, bibliothèques en paysage, trois actions de fiche Film visibles et bouton Retour réellement utilisé, Guide avec EPG et consigne tactile. Le processus 6879 ne présente pas de FATAL EXCEPTION, JNI_ERR ou événement ANR relevé pendant le scénario.

Les comparaisons de captures confirment les gains : le bouton Retour Film n’est plus hors cadre ; le panneau Organisation n’affiche plus Réglages verticalement et laisse davantage de place à la liste ; les affiches, titres, dates et notes de la première rangée Films/Séries sont consultables ensemble en paysage.

Le premier test par commande média a rendu le portrait, mais le test renforcé par le bouton « Épisode suivant », avec identité S01E01 → S01E02 vérifiée, a échoué sur le build 252 : retour sur la fiche en paysage. Les traces montrent requestedOrientation restauré à -1 mais user_rotation passé de 0 à 1 pendant la lecture, même avec accelerometer_rotation=0. Ce résultat invalide toute déclaration de correction complète fondée sur le premier test.

Le build 253 utilise USER_LANDSCAPE à la place de SENSOR_LANDSCAPE : le paysage reste imposé pendant la lecture, mais les capteurs respectent le choix utilisateur d’activer la rotation automatique. Aucune écriture de préférence système ni nouvelle permission n’est ajoutée à l’application. La centralisation reste nécessaire pour conserver l’orientation d’origine entre épisodes. Ce changement seul échoue au même test explicite dans le build 253. Il est conservé pour sa sémantique de respect de la rotation automatique, sans être présenté comme la solution suffisante.

Le build 254 sauvegarde aussi la direction de l’écran à l’entrée. À la sortie, il rétablit cette direction, attend 500 ms pour la transition Android puis rétablit la politique d’orientation initiale. Cette coroutine est annulée si une nouvelle lecture commence ; son état est conservé lors d’une recréation. L’application n’écrit pas les paramètres système. Les scripts de test verrouillent temporairement la rotation pour rendre le scénario reproductible puis rétablissent la configuration initiale de l’émulateur. Validation finale ci-dessous.

Le Galaxy A54 et la Mi Box ne sont pas connectés à ADB ; aucune installation ni validation de ce lot sur ces appareils n’est revendiquée. Le rognage intermittent A38 reste ouvert. Il n’y a pas de nouveau benchmark CPU/RAM, de pourcentage de fluidité gagné ou de fidélité intégrale à une référence revendiqué pour ce lot. Les mesures A54 des lots précédents restent distinctes.

### Compatibilité et priorités restantes

| Environnement | Ce lot | Limites |
|---|---|---|
| Mobile portrait émulé, API 35, 1080 × 2400 | Fiche Film, Organisation médias et chaînes, actions de groupe et Guide inspectés en captures réelles | Aucun changement de visibilité ou de profil pendant les parcours de disposition |
| Mobile paysage émulé, 2400 × 1080 | Grilles Films/Séries comparées avant/après ; lecteurs et retour testés séparément | Pas de mesure de jank ou de consommation comparée pour ce lot |
| Galaxy A54 physique | Non testé dans ce lot | Déconnecté d’ADB ; version installée antérieure conservée |
| Mi Box physique | Non testée dans ce lot | Déconnectée ; les contrôles DPAD et les résolutions physiques restent à revalider |

Priorité suivante : P1/A51, étendre la validation du repli VLC vers Media3 et caractériser la cause de l’erreur VLC avant validation complète des Séries ; P1/A38, rognage intermittent des contrôles vidéo à reproduire sur A54 et moteur concerné ; P1/A48, étendre la validation de rotation au téléphone physique, aux retours rapides et à la rotation automatique ; P2, vérifier les autres panneaux Réglages en petite largeur et les libellés de catégories Chaînes dont le symbole de sélection peut apparaître sous forme de carré ; P2, nouvelle campagne de fluidité et mémoire sur catalogue volumineux avec mesure comparable, puis DPAD Mi Box. Les références cinématographiques des lots précédents restent la cible ; ces comparaisons portent sur une amélioration avant/après de l’application, pas sur une ressemblance chiffrée à une référence.

Aucun commit, push, déclenchement CI ou publication de release. Le dépôt original et les données utilisateur sont conservés.


### Résultats finaux du build 254

Le scénario explicite identifie le passage S01E01 → S01E02 puis revient en 1080 × 2400. Les paramètres de contrôle passent de accelerometer_rotation=0/user_rotation=0 avant entrée à 0/1 dans le lecteur, puis reviennent à 0/0 après Retour. Le processus 10150 reste vivant, sans FATAL EXCEPTION, JNI_ERR ni événement ANR relevé. **Cela valide le changement de route et le retour d’orientation, pas la lecture complète du deuxième épisode.** Sa capture montre « Lecture Media3 impossible (Lecture libVLC impossible) » et une image noire : anomalie **A51/P1 ouverte**, cause réseau, flux, moteur ou transition non encore établie. Le premier épisode présente une image décodée. Aucun succès de lecture du deuxième épisode n’est déduit de sa seule identité.

Le Direct TF1 du profil existant est testé en sélection, aperçu, plein écran paysage puis retour portrait. Deux captures espacées de cinq secondes montrent des images différentes de l’émission. Les trois contrôles de géométrie/retour/santé passent et le processus reste sans crash, JNI_ERR ni ANR relevé. Le libellé fournisseur « 4K » n’est pas une mesure de résolution décodée. La séquence attend plus de deux secondes après le deuxième appui ; la borne temporelle exacte n’a pas été remesurée dans ce lot.

Les échecs intermédiaires 252 et 253 restent conservés avec leurs scripts et captures ; ils ne sont pas remplacés par les traces réussies de 254. Un patch de revue des huit fichiers modifiés dans ce lot, changes252-255.patch, est sauvegardé sur le PC, en complément des sauvegardes avant modification.


### Investigation A51 et correction suivante

La relance directe de S01E02 depuis la fiche, sur le même build 254, affiche deux images différentes à cinq secondes d’intervalle et une progression de 02:30/50:30, sans message d’erreur. Le flux n’est donc pas systématiquement indisponible. Le premier script de relance a échoué sur un séparateur textuel attendu « · » au lieu du « • » réellement présent ; le script corrigé utilise le libellé observé. Cet échec d’automatisation n’est pas un crash applicatif.

L’inspection de VodPlayerScreen.kt révèle une cause applicative précise au blocage du repli : onError VLC prépare Media3 mais appelle aussi VodPlayerViewModel.onPlaybackError(error). Celui-ci remplit state.error avec « Lecture Media3 impossible (…) » ; le when de l’écran choisit cette erreur avant AndroidView. La surface Media3 est ainsi retirée alors que le repli est en cours. Ce libellé ne prouve pas à lui seul que Media3 a réellement échoué. Les journaux filtrés de cette passe ne fournissent d’ailleurs pas de code d’échec Media3 ni de réponse HTTP exploitable.

Le build 255 supprime cette erreur terminale prématurée pour Next : la surface du moteur de secours reste montée ; le listener Media3 conserve son chemin d’erreur terminale si sa propre lecture échoue. Un message de diagnostic sans URL indique le démarrage du repli. La branche originale conserve son comportement. La cause initiale de l’erreur VLC et la reproduction physique du repli restent à caractériser ; cette correction cible le blocage certain de l’interface de secours, sans attribuer l’échec au fournisseur.

Une première application de la correction 255 a été arrêtée sur une assertion de correspondance textuelle ; aucune modification de source n’a été faite par ce script. Une passe de compilation suivante a échoué en 7 s sur la référence BuildConfig non importée ; la référence a été qualifiée explicitement et les traces ont été conservées. Seul le build final réussi est installé.


### Verdict après captures du build 255

Les quatre assertions automatisées passent : aucun libellé terminal, identité S01E01 → S01E02, retour portrait avec rotation 0/0, processus 11562 vivant sans crash/JNI_ERR/ANR relevé. **L’inspection humaine invalide toutefois la lecture E2 : les deux captures montrent une image noire et 00:00, y compris cinq secondes après Reprendre. Le scénario lecture après épisode suivant est donc ÉCHOUÉ.** L’absence d’erreur à l’écran et le succès des assertions de navigation ne sont pas des preuves de lecture. A51 reste P1 ouvert ; seule la publication prématurée d’une erreur terminale pendant le repli est corrigée dans le code.

Les journaux filtrés de ce dernier scénario ne contiennent ni le nouveau message de repli ni une première image Media3 ; le passage VLC → Media3 n’a donc pas été prouvé en exécution pour ce build. Il faut instrumenter séparément le démarrage, le bind/lifecycle et la libération des deux sessions au changement de route, contrôler l’état requested/playing et obtenir une réponse réseau ou une erreur de décodage avant d’attribuer le défaut à une cause supplémentaire. La lecture directe E2 réussie sur 254 ne valide pas le passage E1 → E2. Le titre et certains contrôles VLC restent aussi rognés par intermittence : A38 ouvert.

Verdict du lot : A45/A46/A47/A50 validés sur émulateur avec limites des parcours indiquées ; A48 retour d’orientation validé sur émulateur, validation physique restante ; A51 partiellement corrigé dans le code, lecture entre épisodes toujours en échec. Le Direct réel a réussi sur 254 avec le même gestionnaire d’orientation ; son parcours complet n’est pas revendiqué comme rejoué sous 255. Les préférences de l’émulateur sont rétablies à accelerometer_rotation=1/user_rotation=0, l’application est laissée sur Accueil. L’APK finale est installée uniquement sur émulateur.
