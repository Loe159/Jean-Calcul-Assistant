# Plan complet du projet

## 1. Objectif

Créer une application Android open-source d'assistant personnel dans l'esprit de Gemini, mais contrôlable, auditable et connectée à Hermes.

Hermes est l'orchestrateur principal. Il tourne localement dans Termux ou sur le réseau local, reçoit les requêtes utilisateur, gère la mémoire, décide quels outils appeler, peut lancer des sous-agents, et retourne des actions structurées à l'application Android.

L'application Android se concentre sur :

- l'interface utilisateur ;
- la voix ;
- l'overlay assistant ;
- les permissions Android ;
- l'exécution sécurisée des outils ;
- les notifications ;
- la communication HTTP avec Hermes.

## 2. Contraintes Android à accepter

### Bouton Power

Une application Android standard ne peut généralement pas intercepter librement l'appui long sur le bouton Power. Cette capacité est réservée au système, à l'assistant par défaut, au constructeur, ou à des intégrations privilégiées.

Approche recommandée :

1. assistant numérique par défaut quand Android et le constructeur le permettent ;
2. tuile rapide ;
3. notification persistante ;
4. raccourci launcher ;
5. overlay flottant ;
6. Shizuku ou root en option avancée ;
7. module système ou ROM custom seulement à long terme.

### Contrôle système

Sans root, certaines actions sont simples :

- régler le volume ;
- ouvrir une application ;
- lancer une activité de paramètres ;
- contrôler la lampe torche ;
- afficher des notifications ;
- lire / parler via STT et TTS Android.

D'autres sont restreintes :

- modifier Wi-Fi, GPS ou mode avion ;
- lire l'écran d'une autre application ;
- cliquer automatiquement ;
- modifier des paramètres protégés.

Ces actions doivent passer par des permissions spéciales, l'accessibilité, Shizuku, root ou une intégration système.

## 3. Architecture cible

```text
:app
:ui-designsystem
:ui-overlay
:voice
:hermes-client
:tool-registry
:android-tools
:permissions
:notifications
:security
```

Modules optionnels futurs :

```text
:accessibility-tools
:shizuku-tools
:root-tools
:assistant-service
:local-fallback
```

## 4. Responsabilités par module

### `:app`

- point d'entrée Android ;
- navigation ;
- injection de dépendances ;
- assemblage des modules ;
- configuration générale.

### `:ui-designsystem`

- thème liquid glass ;
- couleurs gris, bleu et vert ;
- composants communs ;
- boutons, cartes, champs et animations de base.

### `:ui-overlay`

- assistant flottant ;
- orb animé ;
- états listening, thinking, speaking, acting, error ;
- affichage transcription / réponse courte ;
- actions annuler et confirmer.

### `:voice`

- Speech-to-text Android ;
- text-to-speech Android ;
- gestion micro ;
- voice activity state ;
- plus tard wake word local.

### `:hermes-client`

- client HTTP ;
- configuration base URL et token ;
- sérialisation des requêtes ;
- health check ;
- envoi des requêtes utilisateur ;
- envoi des résultats d'outils ;
- polling des jobs longs.

### `:tool-registry`

- registre des outils exposables à Hermes ;
- descriptions ;
- niveaux de risque ;
- politiques de confirmation ;
- validation des arguments.

### `:android-tools`

- implémentations concrètes : volume, apps, lampe, notifications, paramètres ;
- encapsulation des APIs Android ;
- résultats normalisés.

### `:permissions`

- état des permissions Android ;
- écrans de demande ;
- explication utilisateur ;
- permissions spéciales comme overlay et notifications.

### `:notifications`

- notifications de jobs Hermes ;
- canal de notification ;
- action au clic ;
- notification persistante éventuelle.

### `:security`

- politique par outil ;
- confirmations locales ;
- journal d'actions ;
- rate limits éventuels ;
- protection des secrets par Android Keystore.

## 5. Flux principal

```text
Utilisateur parle ou écrit
  -> App Android capture l'entrée
  -> App envoie POST /api/mobile/request à Hermes
  -> Hermes répond avec un message et des actions
  -> App valide chaque action localement
  -> App demande confirmation si nécessaire
  -> App exécute les outils autorisés
  -> App renvoie POST /api/mobile/tool-result
  -> App affiche / vocalise la réponse finale
```

## 6. Outils Android prioritaires

### MVP

- `set_volume` ;
- `get_volume` ;
- `open_app` ;
- `toggle_flashlight` ;
- `send_notification` ;
- `open_settings_page`.

### Version sécurité avancée

- `call_contact` avec confirmation ;
- `send_sms` avec confirmation obligatoire ;
- `read_notifications` avec permission spéciale ;
- `create_reminder` ;
- `get_device_status`.

### Version accessibilité

- `summarize_screen` ;
- `click_ui_element` ;
- `input_text` ;
- `scroll` ;
- `observe_foreground_app`.

## 7. Sécurité

Règles non négociables :

- les outils sensibles sont désactivés par défaut ;
- chaque outil est désactivable ;
- les actions sensibles demandent confirmation locale ;
- Hermes ne peut pas contourner la politique locale ;
- les arguments sont validés côté Android ;
- chaque action est journalisée ;
- les tokens sont stockés via Android Keystore ;
- un mode local-only doit être disponible.

## 8. UX

Écrans principaux :

- accueil / conversation ;
- configuration Hermes ;
- test de connexion ;
- permissions ;
- outils activés ;
- journal d'actions ;
- tâches Hermes ;
- paramètres voix ;
- apparence.

Overlay :

- fond transparent ;
- orb bleu-vert ;
- animation selon amplitude micro et état agent ;
- transcription live ;
- réponse courte ;
- bouton annuler ;
- carte de confirmation.

## 9. Jobs longs et sous-agents

Hermes peut répondre avec un `jobId`. L'application affiche une tâche en cours et interroge Hermes périodiquement.

Cas d'usage :

- analyse de document ;
- résumé long ;
- comparaison de fichiers ;
- veille ;
- planification lourde ;
- sous-agents Hermes.

Quand un job se termine, l'application affiche une notification Android.

## 10. Versions

### 0.1

Client HTTP Hermes minimal : texte, réponse, health check.

### 0.2

Outils Android simples : volume, ouvrir app, lampe, notification.

### 0.3

Voix : STT Android, TTS Android, bouton parler.

### 0.4

Overlay Gemini-like.

### 0.5

Sécurité : confirmations, politiques par outil, logs.

### 0.6

Jobs longs, polling, notifications.

### 0.7

Assistant Android par défaut, tuile rapide, notification persistante.

### 0.8

Accessibilité optionnelle.

### 1.0

Version stable utilisable au quotidien.
