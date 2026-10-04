# Compagnon Codex Jean Calcul

Le compagnon permet à Jean Calcul d'utiliser **Codex avec l'abonnement ChatGPT**, sans clé API OpenAI. Les identifiants ChatGPT restent gérés exclusivement par le CLI Codex sur le PC ; Android ne reçoit qu'un jeton d'appairage Jean Calcul aléatoire.

## Prérequis

- Python 3.11+ ;
- le CLI `codex` installé et accessible dans `PATH` ;
- un abonnement ChatGPT donnant accès à Codex ;
- pour le test Android actuel : ADB/USB. Le transport LAN/TLS et l'appairage révocable complet restent l'objet de l'issue #42.

## Première connexion

```bash
python3 companion/codex_companion.py login
python3 companion/codex_companion.py status
python3 companion/codex_companion.py serve
```

`login` utilise le flux officiel `codex login --device-auth`. Aucun secret ChatGPT n'est copié dans Jean Calcul.

Le serveur est volontairement limité à `127.0.0.1`. Pour le téléphone de développement :

```bash
adb reverse tcp:43120 tcp:43120
```

Dans Jean Calcul, créer un fournisseur de type **Agent backend** avec :

- URL : `http://127.0.0.1:43120` ;
- secret : le `Pairing token` affiché par `status` ou `serve` ;
- puis créer un profil agent avec `backendId = codex-companion` et l'activer.

## Sécurité

- le compagnon refuse une écoute HTTP hors loopback ;
- toutes les routes exigent `Authorization: Bearer <pairing token>` ;
- le token est stocké dans `~/.config/jean-calcul/companion.json` avec mode `0600` ;
- Codex tourne avec `approvalPolicy=never` et `sandbox=read-only` ;
- le compagnon désactive explicitement `shell_tool`, `unified_exec`, `code_mode_host` et la recherche Web pour cette phase ;
- le répertoire de travail par défaut est un dossier Jean Calcul isolé et vide, pas le dossier personnel ;
- les instructions du thread interdisent également les opérations sur l'hôte : Android reste l'autorité pour les outils ;
- le serveur ne journalise ni en-têtes, ni prompts, ni tokens.

## Diagnostic

```bash
python3 companion/codex_companion.py status
curl -H "Authorization: Bearer <token>" http://127.0.0.1:43120/v1/status
```

La route de statut ne renvoie ni adresse e-mail, ni access token, ni refresh token.
