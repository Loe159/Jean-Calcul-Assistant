# Phase 5 — Dashboard, mails et automatisations personnelles

## Objectif

Créer la surface centrale permettant de voir la journée, les informations importantes, les décisions en attente, les objectifs et l’état du système.

## Résultat utilisateur attendu

- obtenir un brief quotidien ;
- voir les tâches, événements, notifications et mails importants ;
- générer des brouillons de réponse ;
- rechercher dans ses données ;
- créer des automatisations lisibles ;
- comprendre la source de chaque recommandation.

## Architecture

Composants :

- dashboard Compose ;
- agrégateur Today ;
- providers email ;
- synchronisation incrémentale ;
- classificateur mail ;
- recherche plein texte ;
- moteur de briefs ;
- automatisations ;
- widgets et raccourcis ;
- sauvegarde/synchronisation.

## Navigation

Sections prévues :

- Aujourd’hui ;
- Assistant ;
- Inbox ;
- Tâches ;
- Calendrier ;
- Mails ;
- Projets ;
- Objectifs ;
- Automatisations ;
- Mémoire ;
- Agents ;
- Confidentialité ;
- Doctor ;
- Paramètres.

L’interface doit être adaptative pour téléphone, tablette, pliable et paysage.

## Travaux

### Écran Aujourd’hui

Afficher :

- prochain événement ;
- conflits ;
- tâches prioritaires ;
- notifications importantes ;
- mails à traiter ;
- habitudes ;
- objectifs actifs ;
- alertes système ;
- suggestions.

Chaque suggestion expose raison, source, action, confiance et données utilisées.

### Providers email

Interface `EmailProvider` avec adaptateurs :

- Gmail ;
- Microsoft Graph ;
- IMAP générique.

Fonctions :

- OAuth ou secrets applicatifs adaptés ;
- scopes minimaux ;
- sélection de comptes ;
- synchronisation incrémentale ;
- métadonnées ;
- contenu à la demande ;
- recherche ;
- lu/non lu ;
- archivage ;
- brouillon ;
- envoi confirmé.

### Classification des mails

Catégories : réponse requise, information importante, événement, facture, administratif, travail, personnel, newsletter, promotion et spam probable.

Appliquer les mêmes protections contre l’injection que pour les notifications.

### Résumés

- nouveautés depuis le dernier brief ;
- demandes nécessitant une réponse ;
- échéances ;
- rendez-vous ;
- factures ;
- éléments de projet ;
- contacts prioritaires.

### Brouillons

- générer sans envoyer ;
- afficher le contexte utilisé ;
- permettre plusieurs tons ;
- vérifier les destinataires ;
- signaler les pièces jointes mentionnées ;
- exiger confirmation et biométrie pour l’envoi selon politique.

### Recherche unifiée

Sources : conversations, tâches, événements, notifications, mails, mémoire, objectifs, projets et résultats d’agents.

Commencer par une recherche plein texte locale avec filtres. Les embeddings ne sont ajoutés que si des cas concrets restent mal couverts.

### Brief quotidien

Contenu : agenda, conflits, tâches, retards, notifications, mails, objectifs et propositions de planification.

Déclenchements : heure configurée, première ouverture du matin, commande vocale, widget et notification.

### Automatisations

Modèle :

```text
Déclencheur
Conditions
Action ou proposition
Niveau de confirmation
Canal de résultat
```

Exemples : brief quotidien, proposition d’événement depuis livraison, rappel d’un mail prioritaire sans réponse.

Toute automatisation est lisible, désactivable et journalisée.

### Widgets et raccourcis

- Aujourd’hui ;
- tâches ;
- nouvelle tâche ;
- parler ;
- brief ;
- inbox ;
- éventuelle tuile paramètres rapides.

### Sauvegarde et synchronisation

- export chiffré ;
- import ;
- sauvegarde locale ;
- sauvegarde serveur optionnelle ;
- multi-appareils ;
- résolution de conflits ;
- suppression distante ;
- rétention.

## Difficultés

- scopes email sensibles ;
- validation OAuth publique ;
- HTML et pièces jointes ;
- grands volumes ;
- doublons multi-comptes ;
- recherche transversale ;
- suggestions trop nombreuses ;
- exposition excessive des données ;
- synchronisation et conflits.

## Contraintes

- contenu complet d’un mail chargé seulement si nécessaire ;
- aucun envoi automatique ;
- aucune pièce jointe exécutée ;
- source de chaque suggestion visible ;
- brief personnalisable ;
- mode local sans compte email ;
- automatisations réversibles et désactivables.

## Critères de sortie

- dashboard quotidien utile ;
- au moins un provider email complet ;
- détection de mails importants ;
- brouillons sûrs ;
- recherche unifiée ;
- brief quotidien ;
- automatisations contrôlées ;
- export et restauration ;
- comportement multi-compte documenté.
