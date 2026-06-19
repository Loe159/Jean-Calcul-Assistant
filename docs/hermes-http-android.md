# Architecture HTTP Hermes pour Android Termux

## Décision

Hermes étant exposé localement sur Android via Termux, le client Android doit privilégier un protocole HTTP local plutôt qu'un WebSocket permanent.

Cette approche simplifie le déploiement, fonctionne bien avec un service Hermes écoutant sur `127.0.0.1` ou sur le réseau local, et évite de maintenir une connexion longue durée que le système Android pourrait interrompre en arrière-plan.

## Topologie cible

```text
Utilisateur
  -> Application Android
  -> HTTP localhost / LAN
  -> Hermes dans Termux
  -> Réponse JSON
  -> Exécution sécurisée des outils Android par l'app
```

Exemples d'URL de configuration :

```text
http://127.0.0.1:8765
http://localhost:8765
http://192.168.1.42:8765
```

Sur Android, l'application doit autoriser le trafic clair local si Hermes n'utilise pas HTTPS.

## Responsabilités

### Application Android

- Capture la voix ou le texte utilisateur.
- Envoie la requête à Hermes via HTTP.
- Expose la liste des capacités Android disponibles.
- Vérifie les permissions locales avant toute action.
- Demande confirmation pour les actions sensibles.
- Exécute les outils Android autorisés.
- Renvoie les résultats d'outils à Hermes.
- Affiche les réponses, états de tâche et notifications.

### Hermes dans Termux

- Orchestre la requête.
- Gère la mémoire et le contexte utilisateur.
- Décide quels outils appeler.
- Peut lancer des traitements longs ou sous-agents.
- Retourne à l'application Android des actions structurées à exécuter.

## Protocole HTTP minimal

### Vérification de santé

```http
GET /api/mobile/health
```

Réponse :

```json
{
  "status": "ok",
  "name": "Hermes",
  "version": "1.0.0"
}
```

### Envoi d'une requête utilisateur

```http
POST /api/mobile/request
Content-Type: application/json
Authorization: Bearer <token>
```

Corps :

```json
{
  "requestId": "req_001",
  "input": {
    "mode": "voice",
    "text": "Baisse le volume à 30 %.",
    "locale": "fr-FR"
  },
  "client": {
    "platform": "android",
    "appVersion": "0.1.0"
  },
  "capabilities": [
    {
      "name": "set_volume",
      "risk": "low"
    },
    {
      "name": "open_app",
      "risk": "low"
    },
    {
      "name": "send_notification",
      "risk": "low"
    },
    {
      "name": "send_sms",
      "risk": "high",
      "requiresConfirmation": true
    }
  ]
}
```

Réponse :

```json
{
  "requestId": "req_001",
  "message": "Je baisse le volume à 30 %.",
  "speak": true,
  "actions": [
    {
      "actionId": "act_001",
      "tool": "set_volume",
      "arguments": {
        "stream": "music",
        "level": 30
      }
    }
  ]
}
```

### Retour de résultat d'outil

```http
POST /api/mobile/tool-result
Content-Type: application/json
Authorization: Bearer <token>
```

Corps :

```json
{
  "requestId": "req_001",
  "actionId": "act_001",
  "tool": "set_volume",
  "status": "success",
  "result": {
    "stream": "music",
    "level": 30
  }
}
```

Réponse :

```json
{
  "requestId": "req_001",
  "message": "C'est fait.",
  "done": true
}
```

## Jobs longs sans WebSocket

Pour les traitements lourds ou sous-agents, Hermes peut répondre avec un `jobId`.

```json
{
  "requestId": "req_010",
  "message": "Je lance l'analyse et je te préviens quand c'est terminé.",
  "job": {
    "jobId": "job_123",
    "status": "running",
    "title": "Analyse du document"
  }
}
```

L'application Android peut ensuite interroger Hermes périodiquement.

```http
GET /api/mobile/jobs/job_123
Authorization: Bearer <token>
```

Réponse pendant l'exécution :

```json
{
  "jobId": "job_123",
  "status": "running",
  "progress": 42,
  "message": "Extraction du texte terminée."
}
```

Réponse terminée :

```json
{
  "jobId": "job_123",
  "status": "completed",
  "title": "Analyse terminée",
  "message": "Le résumé est prêt.",
  "notification": {
    "title": "Hermes a terminé",
    "body": "L'analyse du document est prête."
  }
}
```

## Stratégie de polling recommandée

- Poll toutes les 2 secondes tant que l'application est au premier plan.
- Poll toutes les 15 à 60 secondes via WorkManager en arrière-plan.
- Arrêter le polling quand le job est terminé, échoué ou annulé.
- Afficher une notification Android quand Hermes retourne un résultat final.

## Sécurité locale

Même si Hermes tourne en local dans Termux, l'application Android doit conserver une frontière de sécurité stricte.

Règles :

- Un token local doit protéger les endpoints Hermes.
- Les outils sensibles nécessitent toujours une confirmation locale.
- Les arguments d'outils doivent être validés côté Android.
- Hermes ne doit jamais obtenir un accès brut aux APIs Android.
- L'utilisateur doit pouvoir désactiver chaque outil exposé.

## Mode dégradé

Si Hermes est indisponible, l'application peut traiter uniquement quelques commandes locales simples :

- régler le volume ;
- ouvrir une application ;
- activer la lampe torche ;
- créer une notification locale ;
- afficher une erreur explicite pour les requêtes nécessitant l'orchestration Hermes.

## Roadmap HTTP

1. Ajouter l'écran de configuration de l'URL Hermes et du token.
2. Implémenter `GET /api/mobile/health` pour tester la connexion.
3. Implémenter `POST /api/mobile/request`.
4. Implémenter l'exécution locale des actions retournées.
5. Implémenter `POST /api/mobile/tool-result`.
6. Ajouter le polling `GET /api/mobile/jobs/{jobId}`.
7. Ajouter les notifications Android pour les jobs terminés.
8. Ajouter plus tard un mode streaming optionnel via Server-Sent Events si nécessaire.

## Évolution possible : Server-Sent Events

Si le besoin de streaming apparaît, le protocole HTTP peut évoluer sans basculer vers WebSocket complet :

```http
GET /api/mobile/request/{requestId}/events
Accept: text/event-stream
```

Cela permet de streamer les réponses Hermes tout en gardant un modèle HTTP simple.
