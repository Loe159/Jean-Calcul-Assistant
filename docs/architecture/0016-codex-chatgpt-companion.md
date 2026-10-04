# Codex via abonnement ChatGPT — compagnon local

## Décision

Jean Calcul utilise un compagnon local pour accéder à Codex avec l'abonnement ChatGPT, sans clé API OpenAI.

Le compagnon lance le CLI officiel avec `codex app-server --stdio` et utilise son protocole JSONL. Le CLI Codex reste seul propriétaire des identifiants ChatGPT. Android ne reçoit jamais access token, refresh token, cookie ou identifiant de compte.

## Flux

```text
Android
  -> AgentBackend codex-companion
  -> HTTP loopback via adb reverse (phase actuelle)
  -> companion/codex_companion.py
  -> codex app-server
  -> compte ChatGPT authentifié dans Codex
```

Le compagnon vérifie `account/read` au démarrage et refuse tout compte dont `account.type` n'est pas `chatgpt`. Une authentification Codex par clé API est donc explicitement rejetée.

## Protocole Jean Calcul minimal

Toutes les routes exigent un Bearer token d'appairage local, distinct des identifiants ChatGPT.

- `GET /v1/status`
- `POST /v1/sessions`
- `POST /v1/sessions/{id}/resume`
- `POST /v1/sessions/{id}/runs`
- `GET /v1/sessions/{id}/runs/{runId}/events`
- `POST /v1/sessions/{id}/runs/{runId}/cancel`

Le streaming Android utilise du NDJSON ordonné par `sequence`.

## Limites de sécurité de cette étape

Le serveur refuse toute écoute HTTP hors loopback. Pour un appareil de développement, `adb reverse` transporte la connexion USB vers le loopback du PC.

Le transport LAN/TLS, la découverte, la rotation et la révocation complète de l'appairage restent dans l'issue #42. Tant que cette issue n'est pas terminée, il ne faut pas exposer le port du compagnon sur le réseau.

Codex est démarré avec une politique d'approbation `never`, un sandbox read-only et un espace de travail isolé. Le compagnon force aussi la désactivation de `shell_tool`, `unified_exec`, `code_mode_host` et de la recherche Web, puis ajoute des instructions développeur interdisant les opérations hôte. Les outils Android ne sont pas encore exposés à Codex dans cette étape ; ils resteront soumis au registre, au Policy Engine et à l'audit lors de #44.
