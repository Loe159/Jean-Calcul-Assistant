# Phase 3 — Agent Gateway et Hermes

## Objectif

Découpler l’application Android des environnements agents et permettre une exécution fiable sur un ordinateur, serveur domestique ou VPS.

Le Gateway devient l’intermédiaire sécurisé entre Android et Hermes, puis entre Android et d’autres backends agents.

## Résultat utilisateur attendu

- appairer son téléphone par QR code ;
- utiliser Hermes depuis l’interface Android ;
- reprendre une session ;
- recevoir les réponses en streaming ;
- approuver un outil Android demandé par l’agent ;
- lancer une tâche longue et recevoir son résultat ;
- révoquer un appareil.

## Architecture

```text
Android
→ TLS/WebSocket
→ Gateway
→ AgentBackend Hermes
→ proposition d’outil
→ Gateway
→ Android Policy Engine
→ exécution locale
→ résultat
→ Hermes
```

Services Gateway :

- API REST ;
- WebSocket ;
- appairage ;
- registre des appareils ;
- sessions ;
- adaptateurs agents ;
- queue de jobs ;
- stockage ;
- audit ;
- observabilité ;
- sandbox.

## Technologies

- Python 3.12+ ;
- FastAPI/Pydantic ;
- asyncio ;
- SQLAlchemy/SQLModel ;
- SQLite/PostgreSQL ;
- Alembic ;
- Docker Compose ;
- Hermes Agent ;
- OpenTelemetry ;
- Pytest, Ruff et mypy.

## Travaux

### Socle Gateway

- initialiser le projet ;
- gestion de configuration ;
- modèles stricts ;
- logs structurés ;
- `/health`, `/ready`, `/version` ;
- image Docker ;
- Compose ;
- migrations ;
- documentation d’installation et de mise à jour.

### Protocole WebSocket

Événements minimum :

```text
client.hello
session.start
session.resume
user.message
assistant.delta
assistant.completed
tool.proposal
approval.request
approval.decision
tool.result
job.created
job.progress
job.completed
session.error
heartbeat
```

Chaque événement possède version, ID, session, séquence, timestamp, correlation ID et payload validé.

### Appairage

- générer un QR code temporaire ;
- échanger une clé ;
- enregistrer l’appareil ;
- émettre un token/certificat lié ;
- afficher liste et dernière connexion ;
- rotation, expiration et révocation ;
- limiter les tentatives.

### Réseau et reprise

- TLS ;
- reconnexion avec backoff ;
- reprise par numéro de séquence ;
- détection hors connexion ;
- file locale des messages ;
- déduplication ;
- timeout ;
- support réseau local, VPN et nom de domaine.

Pour les résultats en arrière-plan, prévoir FCM pour une distribution Play et une alternative FOSS comme UnifiedPush/ntfy.

### Interface `AgentBackend`

Méthodes :

- `create_session` ;
- `resume_session` ;
- `send_message` ;
- `stream_events` ;
- `cancel` ;
- `list_models` ;
- `list_tools` ;
- `list_skills` ;
- `approve_tool` ;
- `get_status`.

### Adaptateur Hermes

- détecter et vérifier la version ;
- démarrer/arrêter le service selon déploiement ;
- créer et reprendre des sessions ;
- diffuser les événements ;
- changer de modèle ;
- activer les skills ;
- traiter les demandes d’approbation ;
- lancer des jobs et cron ;
- exposer le diagnostic ;
- gérer les erreurs et redémarrages.

Hermes doit fonctionner principalement sur serveur ou ordinateur. L’installation Termux reste expérimentale et ne doit pas devenir une dépendance du produit Android.

### Autres adaptateurs

Préparer ou implémenter selon priorité :

- Claude Code ;
- Codex ;
- backend OpenAI-compatible distant ;
- autres agents MCP.

Les agents capables d’exécuter du shell doivent être isolés dans un workspace ou conteneur limité.

### Pont d’outils mobiles

- valider la proposition côté Gateway ;
- vérifier les permissions du profil ;
- envoyer la proposition à l’appareil cible ;
- afficher la confirmation Android ;
- gérer expiration et refus ;
- exécuter via le registre local ;
- retourner un résultat idempotent ;
- poursuivre la session.

### Jobs longs

- queue persistante ;
- progression ;
- reprise après redémarrage ;
- annulation ;
- résultat ;
- expiration ;
- quotas ;
- concurrence maximale ;
- notification finale.

### Observabilité

Mesurer :

- latence du Gateway ;
- temps du premier événement ;
- reconnexions ;
- erreurs d’agent ;
- outils refusés ;
- jobs actifs ;
- coûts/tokens si disponibles ;
- santé Hermes.

## Difficultés

- NAT et exposition sécurisée ;
- réseau mobile instable ;
- formats de sessions propriétaires ;
- reprise après crash ;
- événements en double ;
- agents exécutant du shell ;
- longue durée des travaux ;
- compatibilité de versions Hermes.

## Contraintes

- TLS obligatoire en dehors d’un mode local explicitement signalé ;
- appareil révocable ;
- aucun outil Android exécuté sur le Gateway ;
- Policy Engine Android toujours autoritaire ;
- aucune requête HTTP maintenue pendant un job long ;
- aucune clé mobile transmise à Hermes ou à un skill.

## Critères de sortie

- installation auto-hébergeable ;
- appairage et révocation ;
- streaming fiable ;
- reprise après coupure ;
- Hermes utilisable ;
- outil mobile approuvé de bout en bout ;
- jobs persistants ;
- diagnostics et métriques disponibles.
