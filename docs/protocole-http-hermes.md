# Protocole HTTP Hermes

Ce protocole est volontairement simple pour fonctionner avec Hermes exposé localement via Termux.

## Base URL

Exemples :

```text
http://127.0.0.1:8765
http://localhost:8765
http://192.168.1.42:8765
```

## Authentification

Chaque endpoint non public doit accepter :

```http
Authorization: Bearer <token>
```

Le token est généré ou configuré côté Hermes et stocké côté Android avec Android Keystore.

## Endpoints

### `GET /api/mobile/health`

But : tester la connexion et afficher l'état Hermes.

Réponse :

```json
{
  "status": "ok",
  "name": "Hermes",
  "version": "1.0.0"
}
```

### `POST /api/mobile/request`

But : envoyer une requête utilisateur et les capacités disponibles.

Requête :

```json
{
  "requestId": "req_001",
  "input": {
    "mode": "voice",
    "text": "Ouvre Spotify",
    "locale": "fr-FR"
  },
  "client": {
    "platform": "android",
    "appVersion": "0.1.0"
  },
  "capabilities": [
    { "name": "open_app", "risk": "low" },
    { "name": "set_volume", "risk": "low" }
  ]
}
```

Réponse :

```json
{
  "requestId": "req_001",
  "message": "J'ouvre Spotify.",
  "actions": [
    {
      "actionId": "act_001",
      "tool": "open_app",
      "arguments": {
        "package": "com.spotify.music"
      }
    }
  ]
}
```

### `POST /api/mobile/tool-result`

But : informer Hermes du résultat d'une action locale.

Requête :

```json
{
  "requestId": "req_001",
  "actionId": "act_001",
  "tool": "open_app",
  "status": "success",
  "result": {
    "opened": true
  }
}
```

Réponse :

```json
{
  "requestId": "req_001",
  "message": "C'est ouvert.",
  "done": true
}
```

### `GET /api/mobile/jobs/{jobId}`

But : interroger l'état d'un job long.

Réponse en cours :

```json
{
  "jobId": "job_123",
  "status": "running",
  "progress": 50,
  "message": "Analyse en cours."
}
```

Réponse terminée :

```json
{
  "jobId": "job_123",
  "status": "completed",
  "message": "Le rapport est prêt.",
  "notification": {
    "title": "Hermes a terminé",
    "body": "Le rapport est prêt."
  }
}
```

## Codes d'erreur recommandés

- `400` : requête invalide ;
- `401` : token absent ou invalide ;
- `404` : job ou endpoint inconnu ;
- `408` : traitement trop long, créer un job ;
- `429` : trop de requêtes ;
- `500` : erreur Hermes ;
- `503` : Hermes indisponible ou modèle non chargé.

## Streaming optionnel

Si besoin de streaming plus tard, utiliser SSE :

```http
GET /api/mobile/request/{requestId}/events
Accept: text/event-stream
```

Cela conserve un modèle HTTP tout en permettant de recevoir des tokens ou événements progressifs.
