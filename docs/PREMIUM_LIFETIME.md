# TVFlow Premium, achat unique à vie

## Offre de lancement
- Prix proposé : **4,99 €** TTC, tarif final et devise locale à définir dans Google Play Console.
- Produit géré non consommable : `tvflow_premium_lifetime`. Aucun abonnement.
- Une seule application et un seul compte Google Play par licence ; restauration des achats après validation de la propriété.

## Inclus dans TVFlow Free
Xtream Codes, Live, EPG, VOD et séries, favoris, recherche, audio/sous-titres, correctifs de stabilité et compatibilité serveurs restent accessibles sans achat.

## Premium (évolutif)
- Thèmes et personnalisation avancée, expérience TVFlow CINEMA améliorée.
- Profils multiples et synchronisation téléphone/TV : fonctionnalités à développer, non vendables comme déjà livrées.
- Le prix de l'achat ne doit pas faire croire à la fourniture de contenus IPTV.

## État réel de l'intégration
- Catalogue et écran d'information intégrés aux réglages TV et mobile.
- L'état reste **Free** tant qu'une licence n'a pas été vérifiée par un backend de confiance.
- Checkout désactivé : aucun achat réel, aucun faux déblocage, aucun bouton de paiement trompeur.
- La partie Play Billing (catalogue Google, achat, validation serveur, acquittement, restauration, remboursements, hors ligne) reste à implémenter et tester après création du produit dans Play Console.
- APK GitHub : un canal de licence propre est nécessaire si les achats doivent aussi fonctionner hors Play. Ne jamais considérer une valeur modifiable sur l'appareil comme preuve d'achat.

## Critères de lancement
Tester via piste interne Google Play avec comptes testeurs, achats test, restauration après réinstallation, annulation/remboursement, changement de compte, perte du réseau, compatibilité Mi Box et Android mobile. Vérifier les prix localisés sur la fiche produit.
## Avancement Google Play Billing
- Client Billing 9.1.0 integre : connexion, produit INAPP, prix localise et interrogation des achats possedes.
- Bouton de restauration disponible pour les installations Play Store.
- Un achat detecte n'est pas une licence valide : la verification serveur est obligatoire.
- Checkout bloque tant que verification serveur, acquittement et revocation ne sont pas actifs.
- A finaliser : produit Play Console a 4,99 EUR, service Google Play Developer API, gestion des remboursements, tests par piste interne avec comptes testeurs.