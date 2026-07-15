# Phase 2 — Notifications, tâches et calendrier

## Objectif

Transformer les notifications en une inbox intelligente capable de filtrer, résumer et proposer des actions, sans permettre à un contenu externe de contrôler directement l’assistant.

## Résultat utilisateur attendu

L’utilisateur peut :

- consulter les notifications importantes ;
- retrouver celles qui ont été masquées ou résumées ;
- créer des règles compréhensibles ;
- convertir une notification en tâche ;
- proposer puis confirmer un événement de calendrier ;
- recevoir un résumé à des horaires configurés.

## Architecture

Composants :

- `NotificationListenerService` ;
- normaliseur local ;
- moteur de règles déterministes ;
- classificateur ;
- détecteur de données sensibles ;
- inbox Room ;
- module tâches ;
- adaptateur Calendar Provider ;
- workers de résumé.

Flux :

```text
Notification publiée
→ normalisation locale
→ politique de confidentialité
→ règles déterministes
→ classification optionnelle
→ stockage minimal
→ proposition d’action
→ confirmation
→ tâche ou événement
```

## Technologies

- NotificationListenerService ;
- Room ;
- WorkManager ;
- Calendar Provider ;
- RemoteInput lorsque disponible ;
- classificateur local ou LLM selon politique ;
- chiffrement et redaction existants.

## Travaux

### Accès et onboarding

- déclarer le listener ;
- guider vers les paramètres d’accès ;
- afficher l’état de connexion ;
- gérer perte et restauration de permission ;
- exclure les notifications internes de l’application.

### Normalisation

Extraire uniquement les champs nécessaires :

- package et nom d’application ;
- canal ;
- catégorie ;
- titre ;
- texte ;
- texte développé ;
- horodatage ;
- groupe ;
- conversation/contact éventuel ;
- actions disponibles ;
- importance ;
- persistance ;
- sensibilité.

Ne pas stocker par défaut images, tokens, OTP et contenu bancaire complet.

### Règles déterministes

Conditions :

- application/canal ;
- contact ;
- mots-clés ou expression régulière ;
- catégorie ;
- plage horaire ;
- fréquence ;
- état de concentration ;
- présence d’une action.

Actions :

- conserver ;
- rendre silencieuse ;
- placer dans un résumé ;
- masquer rapidement ;
- marquer importante ;
- proposer tâche/événement ;
- interdire tout envoi au LLM.

### Classification

Catégories : urgente, importante, action requise, événement, tâche, informative, sociale, promotionnelle, distrayante, sensible et inconnue.

Ordre recommandé :

1. règle utilisateur ;
2. heuristique locale ;
3. petit modèle local éventuel ;
4. LLM distant seulement si autorisé et nécessaire.

Conserver score, moteur, justification courte et correction utilisateur.

### Protection contre l’injection

- encapsuler le contenu comme donnée externe ;
- ignorer toute instruction contenue dans le texte ;
- ne jamais appeler un outil depuis la seule notification ;
- exiger une confirmation ou une règle déterministe créée par l’utilisateur ;
- conserver l’origine de la proposition.

### Inbox

Vues : importantes, à traiter, résumées, masquées, toutes et règles.

Actions : ouvrir, archiver localement, supprimer la notification Android, créer une tâche, proposer un événement, répondre si disponible, toujours traiter ainsi, exclure l’application.

### Tâches

Modèle : titre, description, statut, priorité, échéance, projet, tags, récurrence, source et lien vers notification.

Fonctions : création vocale, création depuis notification, report, completion, suppression, recherche et filtres.

### Calendrier

- sélectionner le calendrier cible ;
- lire les événements nécessaires ;
- créer un brouillon ;
- détecter doublons et conflits ;
- gérer fuseau horaire ;
- confirmer ;
- créer, modifier ou supprimer selon politique.

### Résumés

- matin ;
- midi ;
- soir ;
- par application ;
- fin de mode concentration ;
- à la demande.

WorkManager doit regrouper les traitements et respecter la batterie.

### Apprentissage explicable

Après plusieurs décisions similaires, proposer une règle. Ne jamais créer silencieusement une règle de suppression.

## Difficultés

- formats de notifications hétérogènes ;
- regroupements et mises à jour ;
- faux positifs ;
- suppression trop rapide ;
- actions RemoteInput absentes ;
- informations sensibles ;
- événements ambigus ou doublons ;
- restrictions batterie constructeur.

## Contraintes

- le listener observe après publication, il ne garantit pas une interception préalable ;
- les contenus sensibles ne sont pas transmis par défaut ;
- aucune action critique automatique ;
- historique des éléments masqués ;
- règles réversibles ;
- classification toujours surmontable par l’utilisateur.

## Critères de sortie

- inbox utilisable ;
- règles locales prioritaires ;
- classification explicable ;
- protection contre injection testée ;
- tâches créées depuis notifications ;
- calendrier après confirmation ;
- résumés planifiés ;
- correction et restauration possibles.
