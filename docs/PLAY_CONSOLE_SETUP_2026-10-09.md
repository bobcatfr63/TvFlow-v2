# TVFlow V2 — préparation Google Play du 9 octobre 2026

## État constaté
- Application créée en brouillon : TVFlow, package `com.tvflow.app`, français (France), gratuite.
- Play Console app ID : `4972006888101353356`.
- Dépôts conservés : `bobcatfr63/TvFlow-v2` et `bobcatfr63/tvflowV2_release`.
- Textes de fiche enregistrés en brouillon : lecteur IPTV, Android TV et téléphone, sans fourniture de contenus ni identifiants.
- Android TV ajouté aux facteurs de forme ; distribution TV non activée (captures et règles d'examen encore requises).
- Liste `Testeurs internes TVFlow` sélectionnée avec le compte du développeur ; canal encore inactif.
- Release interne brouillon et notes françaises enregistrées. Aucun AAB importé, aucun déploiement.
- Déclarations enregistrées : pas d'annonces, pas d'identifiant publicitaire, pas de service financier, pas de fonctionnalité santé, pas d'organisme gouvernemental.

## Compilation réellement exécutée
- Machine : DESKTOP-8VD83BL ; environnement et cache G:\DevServer ; aucun runner cloud.
- Source : commit `c216ba4` (code Billing) ; aucune modification du code pour cette préparation.
- Gradle : `:app:testDebugUnitTest :app:bundlePlayRelease --no-daemon --no-configuration-cache`.
- Résultat : BUILD SUCCESSFUL ; 47 tests, 0 échec, 0 erreur.
- AAB : `G:\DevServer\repos\TvFlow-v2\app\build\outputs\bundle\playRelease\app-playRelease.aab`.
- Taille : 102924873 octets ; SHA-256 : `6778FE3C0EFEB466D27DA155D80FD35A68A1CEAB2A74D23C9FEE94280A821B4C`.
- Métadonnées de cette compilation : versionName `1.0.79` (VERSION du dépôt), versionCode `2020100086`. Le canal APK DevServer utilise un autre versionName fourni par son environnement ; ne pas annoncer cet AAB comme 1.0.91.
- Signature avec la clé TVFlow EXISTANTE, sans création de clé ; vérification jarsigner : `jar verified`.
- Log : `G:\DevServer\logs\tvflow-play-preparation-20261009.log`.
- Le manifeste Play fusionné exclut REQUEST_INSTALL_PACKAGES et AD_ID. Les tests unitaires ne prouvent pas la lecture réelle ni la qualité TV/mobile.

## Premium à configurer (pas encore créé)
- Produit non consommable INAPP : `tvflow_premium_lifetime`.
- Nom : TVFlow Premium à vie.
- Prix France souhaité : 4,99 EUR, achat unique ; aucun abonnement.
- Description de travail : Débloquez les fonctionnalités Premium de TVFlow par un achat unique. Aucun contenu ni abonnement IPTV n'est inclus. Publier uniquement les avantages effectivement livrés.
- Play Console bloque les produits ponctuels : compte marchand Google Payments manquant.
- Ne pas créer le compte financier sans intervention/confirmation du titulaire.
- Checkout applicatif désactivé tant que validation fiable, acquittement et révocation ne sont pas opérationnels.

## Signature et décisions encore nécessaires
- Play Console a attribué automatiquement une clé de distribution Google lors de la création du brouillon ; aucune action de changement de clé effectuée.
- La clé d'importation ne sera affichée qu'après le premier AAB.
- Avant import, décider de la stratégie de signature Play/GitHub : mêmes signatures nécessaires pour mises à jour directes entre canaux.
- Aucun changement ou création volontaire de clé définitive, aucun paiement, aucune publication en production.

## Éléments restant à préparer/valider
- Icône Store 512x512, image de présentation 1024x500, captures réelles téléphone et TV avec contenu original ou autorisé.
- Politique de confidentialité publique et accessible ; Data safety après audit de tous les flux et SDK, notamment fournisseur Xtream, Cast et Billing.
- Accès examinateur : environnement de démonstration autorisé et instructions ; ne pas transmettre d'identifiants personnels IPTV.
- Classification et cible adaptées aux contenus effectivement accessibles ; ne pas déclarer tout public sans examen.
- Tests physiques Live/VOD/séries, reprise réseau, navigation D-pad/tactile, audio/sous-titres, achats test/restauration/révocation.
- Vérifier les bibliothèques natives 64 bits et pages 16 Ko pour Android TV.

## Références officielles vérifiées
- Propriété intellectuelle : https://support.google.com/googleplay/android-developer/answer/9888072
- Distribution Android TV : https://developer.android.com/training/tv/publishing/distribute
- Achats uniques : https://developer.android.com/google/play/billing/lifecycle/one-time
