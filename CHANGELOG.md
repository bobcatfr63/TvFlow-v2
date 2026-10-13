# Historique des mises à jour TVFlow-v2

## 1.0.122 (en validation)

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
