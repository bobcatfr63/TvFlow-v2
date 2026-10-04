# Audit de consolidation TVFlow

Date : 2026-10-04

## Dépôts identifiés

- `bobcatfr63/TVFlow` : dépôt Android actif historique, privé.
- `bobcatfr63/iptv` : dépôt IPTV legacy, privé.
- `bobcatfr63/IPTV-Releases` : ancien dépôt public de distribution des APK.
- `bobcatfr63/TvFlow-v2` : nouveau dépôt canonique.

## État retenu

`TvFlow-v2` devient l'unique destination pour le développement TVFlow.

Le dépôt canonique doit contenir :
- le code Android TV/mobile ;
- les scripts ADB et de validation ;
- les workflows Forgejo du runner Windows ;
- `devserver.yml` et les métadonnées DevServer ;
- la documentation technique et les audits ;
- les petits ZIP/snapshots historiques déjà versionnés ;
- les historiques des anciens dépôts via des branches `archive/*`.

Les APK ne doivent pas être commités dans Git. Ils doivent vivre dans les Releases de `TvFlow-v2`.

## Archives prévues

- `archive/tvflow-v1-history` : historique complet de l'ancien dépôt TVFlow.
- `archive/iptv-legacy` : historique de l'ancien dépôt IPTV.
- `archive/iptv-releases-manifests` : historique Git du dépôt IPTV-Releases.

## APK historiques

L'ancienne release `tvflow-latest` contient environ 10,4 Go d'assets, dont 48 APK. Beaucoup correspondent à des builds intermédiaires accumulés dans la même release.

Décision de consolidation :
- migrer le dernier APK stable courant et ses métadonnées `release.json` / `sha256.txt` vers `TvFlow-v2` ;
- conserver l'inventaire complet des anciennes releases dans `docs/legacy-releases.json` ;
- publier tous les nouveaux APK exclusivement dans les Releases de `TvFlow-v2` ;
- éviter de réaccumuler des dizaines d'APK dans `tvflow-latest`, avec une rétention limitée aux builds réellement utiles.

## PC Windows / DevServer

Chemin canonique :
`G:\DevServer\repos\TvFlow-v2`

Runner :
`windows-native`

Builder local v2 :
`G:\DevServer\builder\run-build-isolated.ps1`

Le workflow v2 doit exécuter localement :
1. import/consolidation initiale si nécessaire ;
2. lint et tests ;
3. compilation ;
4. validation ADB sur appareil ;
5. publication de l'APK validé.

Aucun runner cloud ne doit être utilisé pour TVFlow sauf demande explicite.

## Anciennes destinations

Les anciens dépôts ne doivent plus recevoir de développement TVFlow une fois la migration confirmée. Ils restent conservés comme sources d'archive jusqu'à vérification complète de la consolidation.
