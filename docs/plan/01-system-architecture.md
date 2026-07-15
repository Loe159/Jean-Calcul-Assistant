# Architecture système cible

## Vue d’ensemble

```text
┌──────────────────────────────────────────────┐
│                 Application Android          │
│                                              │
│ Assistant système / session vocale           │
│ Interface principale / dashboard             │
│ Notification Listener                        │
│ Mobile Tool Bridge                           │
│ Policy Engine                                │
│ Room / DataStore / Keystore / Audit           │
└──────────────────────┬───────────────────────┘
                       │ HTTPS + WebSocket
┌──────────────────────▼───────────────────────┐
│                 Agent Gateway                │
│ Authentification des appareils               │
│ Sessions et streaming                        │
│ Registre des agents et fournisseurs          │
│ Jobs longs et planifiés                      │
│ Connecteurs externes                         │
└──────────┬────────────┬─────────────┬─────────┘
           │            │             │
        Hermes      LLM directs    LifeOS
```

## Composants Android

### `assistant-service`

Responsabilités :

- implémenter `VoiceInteractionService` ;
- exposer l’application comme assistant Android ;
- réagir aux événements de cycle de vie du rôle ;
- lancer ou connecter la session.

Contraintes :

- processus minimal ;
- aucune initialisation réseau lourde ;
- aucune base Room ouverte au démarrage du service ;
- aucune logique LLM ;
- aucune UI principale.

### `assistant-session`

Responsabilités :

- implémenter `VoiceInteractionSessionService` et `VoiceInteractionSession` ;
- afficher la surface transparente ;
- piloter les états d’écoute et de réponse ;
- héberger l’UI Compose de session ;
- transmettre les événements au domaine.

La session doit pouvoir fonctionner dans un processus distinct afin qu’une erreur d’UI ou de voix ne tue pas le service assistant.

### `app`

Responsabilités :

- activité principale ;
- navigation ;
- onboarding ;
- dashboard ;
- configuration ;
- historique ;
- diagnostics.

### `core-domain`

Contient les modèles et interfaces indépendants d’Android :

- `ModelProvider` ;
- `AgentBackend` ;
- `AssistantState` ;
- `ToolDefinition` ;
- `ActionProposal` ;
- `PolicyDecision` ;
- `AuditEvent` ;
- cas d’usage.

### `core-data`

Responsabilités :

- Room ;
- DataStore ;
- repositories ;
- migrations ;
- rétention ;
- export et import.

### `core-network`

Responsabilités :

- clients HTTP et WebSocket ;
- streaming ;
- timeouts ;
- reconnexion ;
- normalisation des erreurs ;
- redaction des logs.

### `core-security`

Responsabilités :

- Android Keystore ;
- `SecretStore` ;
- biométrie ;
- décisions liées au verrouillage ;
- redaction ;
- validation des permissions.

### `core-ui`

Contient le design system :

- surfaces glass ;
- orbe et onde vocale ;
- cartes d’action ;
- feuilles de confirmation ;
- indicateurs de confidentialité ;
- thèmes et accessibilité visuelle.

### `tool-bridge`

Responsabilités :

- registre des outils ;
- validation des schémas ;
- découverte selon l’appareil ;
- exécution Android ;
- idempotence ;
- résultats structurés.

### Features

Modules prévus :

- conversation ;
- voice ;
- notifications ;
- tasks ;
- calendar ;
- email ;
- dashboard ;
- settings ;
- automations ;
- memory.

### `automation-accessibility`

Module optionnel réservé à la variante Power User.

Contraintes :

- désactivé par défaut ;
- aucune dépendance inverse depuis Core ;
- actions déterministes et déclarées ;
- journalisation obligatoire ;
- aucun plan de clic libre produit par un LLM.

## Modèles et agents

### `ModelProvider`

Un fournisseur direct accepte des messages et renvoie un flux normalisé. Il expose ses capacités : texte, outils, vision, audio, limites et contexte.

### `AgentBackend`

Un backend agent gère :

- création et reprise de session ;
- streaming d’événements ;
- liste des outils et skills ;
- interruption ;
- approbation d’outils ;
- jobs longs ;
- références de mémoire propres au backend.

Les identifiants de session d’un agent ne doivent jamais être confondus avec l’historique local d’une conversation directe.

## Cycle d’une action Android

```text
Entrée utilisateur
→ interprétation modèle/agent
→ ActionProposal structurée
→ validation JSON Schema
→ vérification de disponibilité de l’outil
→ Policy Engine
→ confirmation ou biométrie éventuelle
→ exécution déterministe
→ vérification du résultat
→ ToolResult
→ AuditEvent
→ retour au modèle/agent et à l’utilisateur
```

Aucun chemin alternatif ne doit permettre de contourner cette chaîne.

## Agent Gateway

Le Gateway sera introduit en phase 3. Il fournit :

- appairage des appareils ;
- authentification et révocation ;
- sessions WebSocket ;
- adaptateurs Hermes et autres agents ;
- queue de jobs ;
- exécution sandboxée ;
- connecteurs externes ;
- métriques et diagnostic.

L’application Android doit néanmoins fonctionner sans Gateway pour les fonctions locales et les fournisseurs directs configurés sur le téléphone.

## Flux de données

### Conversation directe

Android → `ModelProvider` → streaming → UI → éventuel outil local.

### Agent distant

Android → Gateway → `AgentBackend` → proposition d’outil → Gateway → Android → Policy Engine → exécution → résultat → Gateway → agent.

### Notification

Android Notification Listener → normalisation locale → règles → classificateur optionnel → proposition → utilisateur → tâche/calendrier.

## Contraintes de dépendances

- Les features dépendent du domaine, jamais l’inverse.
- Les providers implémentent des interfaces du domaine.
- Le registre d’outils ne dépend pas de l’UI.
- Le Policy Engine ne dépend pas d’un fournisseur LLM.
- Le Gateway ne doit pas connaître les détails internes Compose.
- Les skills ne reçoivent que les outils explicitement accordés.
