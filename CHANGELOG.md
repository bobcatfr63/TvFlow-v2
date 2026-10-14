# Historique des mises à jour TVFlow-v2

## 1.0.129 (en préparation)

- **Mises à jour fiables** : versionCode désormais déterministe selon le numéro de version TVFlow, y compris avec un historique Git partiel du runner local ; évite les blocages d'installation liés à un code Android inférieur.
- **TNT** : conserve les 25 chaînes reconnues, la numérotation nationale et le libellé « ORDRE : TNT » vérifiés sur la Mi Box.

## 1.0.128

- **TNT** : ordre national TF1, France 2, France 3, France 4 et suivants dans le Guide TV et le Direct, sans appliquer le tri alphabétique à cette catégorie.
- **Validation** : contrôle sur la Mi Box des chaînes visibles, doublons et lecture TV selon les capacités du serveur.

## 1.0.127

- **TNT** : filtre commun au Direct et Guide, reconnaissance stricte des chaînes nationales sans accepter de faux numéros de chaîne fournis par le serveur.
- **Doublons et préférences** : une seule variante de chaque chaîne, priorité aux flux fonctionnels avec EPG et meilleure qualité ; exclusion des chaînes masquées.
- **Tests** : nouveaux contrôles de non-régression sur les numéros erronés, doublons et chaînes masquées.

## 1.0.126

- **TVFlow AI** : recherche de films similaires à une référence présente dans le catalogue (« un film comme Interstellar »), avec comparaison prudente des thèmes de synopsis et sans proposer de films absents du serveur.
- **Qualité des recommandations** : message explicite si le film de référence ou ses métadonnées sont indisponibles ; nouveaux tests sur la reconnaissance de la demande et les points communs des synopsis.

## 1.0.125

- **TVFlow AI** : meilleure compréhension des demandes naturelles en français ; les mots de conversation inutiles sont exclus de la recherche tandis que les genres et périodes sont conservés.
- **Tests IA** : nouveaux cas couvrant « bon film d'action récent », « bonne série policière française » et « nouveau film ».

## 1.0.124

- **TVFlow AI** : meilleure pertinence des recherches françaises, en privilégiant les mots entiers et leurs variantes longues dans les titres et synopsis ; réduction des faux résultats liés aux morceaux de mots.
- **Tests IA** : nouveaux cas sur les correspondances parasites et le classement des titres face aux synopsis.

## 1.0.123

- **Guide TV** : retrait de l'entrée « Toutes les chaînes » devenue redondante avec l'organisation du catalogue dans les réglages.
- **Navigation** : ouverture du Guide sur la catégorie TNT ; favoris et catégories du serveur restent accessibles, les chaînes masquées ne sont pas supprimées.

## 1.0.122

- **Guide et Direct** : nouvelle catégorie **TNT** regroupant les chaînes nationales reconnues, indépendamment des catégories IPTV du serveur ; retrait du mode de tri TNT.
- **Thème Rainbow** : animation de couleur renforcée sur le contour fixe, notamment sur les catégories du Guide ; contours animés uniquement lorsque le thème est sélectionné.

## 1.0.121

- **TVFlow AI** : barre de recherche compacte dès la saisie pour laisser plus de place aux films et séries ; résultats et messages réunis dans la zone défilante ; réinitialisation du défilement à chaque nouvelle recherche.
- **Rainbow** : contour animé étendu aux champs de recherche, au micro et aux suggestions de TVFlow AI lorsque le thème Arc-en-ciel est sélectionné.

## 1.0.120

- **Nouveau thème Arc-en-ciel animé** : contours colorés en mouvement autour des onglets sélectionnés et des cartes de l'accueil. Choix du thème dans les réglages, avec focus TV lisible.
- **Versions** : incrémentation du numéro affiché à chaque nouvelle publication (1.0.120, puis 1.0.121, etc.), en conservant un code Android toujours croissant.

## Modifications précédentes

- **Direct et Guide TV** : tri TNT réellement numéroté, tri A à Z amélioré en ignorant les préfixes IPTV et indications de qualité. Même logique dans les deux pages.

- **Films et séries** : le filtre par année et par mois recherche désormais dans toutes les catégories du profil IPTV actif.
- **Mises à jour** : la détection et les notifications consultent le même manifeste de distribution ; numérotation des builds corrigée pour éviter les rétrogradations.
- **TVFlow AI** : grille de suggestions adaptée à la télévision, contrastes renforcés et recherche Xtream optimisée.
- **Nouveautés** : le pop-up après installation s'affiche pour chaque nouveau code de build, même si le numéro de version affiché reste identique, et utilise des descriptions lisibles.

La publication de cette prochaine version devra reprendre ces nouveautés dans `release.json` et dans les notes GitHub. Ne pas publier sans nouvelle vérification de la signature, du code de version et des tests.
