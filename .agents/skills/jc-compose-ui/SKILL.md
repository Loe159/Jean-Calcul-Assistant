---
name: jc-compose-ui
description: "Améliorer les écrans Compose existants de Jean Calcul : conversation, navigation, réglages, audit, états et accessibilité. Préserve les comportements et tokens sans nouvelle identité arbitraire."
---

# jc-compose-ui

## Entrée

Lire JC-07 ou JC-08, [la spec UX](../../../docs/specs/06-ux-and-design.md), instructions core-ui,
[design Compose](../../../docs/design/compose-design-system.md) et écrans réels. Distinguer
core-ui primitives, previews et intégration app ; une preview ne prouve pas le parcours.

## Construire dans le système existant

Utiliser Material 3, rôles de thème et tokens DesignTokens. Conserver les thèmes/effets réduits.
Organiser la conversation en contexte/destination, messages flexibles et composeur au-dessus de
l'IME. Export/suppression sont secondaires ; stop/retry restent attachés au bon tour. Appliquer
insets plutôt qu'un padding supposé couvrir toute navigation/clavier.

Respecter les contrats d'état livrés par le runtime. Aucun faux succès, activité micro simulée,
profil invisible, enum brute ou erreur d'export utilisée pour une erreur d'envoi. Les permissions
et confirmations se décident hors Composable ; UI n'est pas autorité de politique.

Dans les réglages, séparer connexion/secret/profil actif/capacités. Montrer un statut auth honnête.
Dans l'audit, distinguer décision et exécution, refus, erreur et rejeu.

## Vérifier en lots

Captures Android téléphone compact/large, tailles étendues supportées, sombre/clair, IME, police
1,3× et contrôle 2×, effets réduits et états empty/stream/error/confirmation. Vérifier TalkBack,
48 dp, focus et Retour. Conserver position de lecture si le lecteur n'est pas en bas.
Corriger les défauts matériels en lot et confirmer ; éviter une boucle de retouches sans fin.

## Sortie

Code et checks de mission, captures datées au commit et matrice des états réellement vus.
L'absence d'émulateur/appareil est une limite de preuve, pas une capture fictive. Ne pas changer
les droits d'outils ou la politique pour alléger visuellement le parcours.
