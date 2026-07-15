# Roadmap et dépendances

## Ordre global

```text
Phase 0 — Validation Android/Samsung
  ↓
Phase 1 — Assistant vocal MVP
  ├── Phase 2 — Notifications, tâches, calendrier
  └── Phase 3 — Gateway et Hermes
        ↓
      Phase 4 — Skills, mémoire et LifeOS
        ↓
      Phase 5 — Dashboard et mails
        ↓
      Phase 6 — Finalisation et livraison
```

La phase 6 est également transversale : sécurité, tests et observabilité commencent dès la phase 0.

## Dépendances principales

### Phase 0 → Phase 1

Bloquants :

- rôle assistant Android ;
- session transparente ;
- invocation Power Samsung ;
- STT/TTS ;
- premier outil déterministe ;
- parcours complet.

Sans validation de ces points, ne pas investir dans les intégrations de fournisseurs.

### Phase 1 → Phase 2

Pré-requis :

- registre d’outils ;
- Policy Engine ;
- audit ;
- stockage local ;
- redaction ;
- UI de confirmation.

La classification des notifications doit réutiliser ces mécanismes et non créer un second chemin d’action.

### Phase 1 → Phase 3

Pré-requis :

- contrats `AgentBackend` ;
- streaming normalisé ;
- approbation d’outils ;
- profils et secrets ;
- reprise de conversation.

### Phase 2 + Phase 3 → Phase 4

Les skills et hooks ont besoin :

- d’outils mobiles stables ;
- d’un Gateway capable de sandbox ;
- de données personnelles structurées ;
- d’un moteur de permissions mature.

### Phase 4 → Phase 5

Le dashboard avancé utilise :

- mémoire ;
- objectifs/TELOS ;
- hooks ;
- agents ;
- Doctor ;
- tâches et calendrier.

Les mails peuvent commencer plus tôt comme expérimentation isolée, mais leur intégration au dashboard suit cette dépendance.

## Critères de passage

### Sortie phase 0

- parcours vocal volume validé sur Samsung ;
- stabilité et budgets documentés ;
- limites constructeur connues.

### Sortie phase 1

- assistant utilisable ;
- fournisseurs configurables ;
- actions locales sécurisées ;
- audit et secrets ;
- mode hors connexion.

### Sortie phase 2

- inbox fiable ;
- règles ;
- tâches/calendrier ;
- injection testée ;
- résumés.

### Sortie phase 3

- appairage ;
- Hermes ;
- streaming/reprise ;
- outils mobiles ;
- jobs longs.

### Sortie phase 4

- skills versionnés ;
- permissions ;
- sandbox ;
- TELOS/mémoire ;
- import LifeOS ;
- Doctor.

### Sortie phase 5

- dashboard ;
- email ;
- recherche ;
- brief ;
- automatisations ;
- sauvegarde.

### Sortie phase 6

- sécurité auditée ;
- performances et batterie validées ;
- builds distribuables ;
- récupération et support.

## Parallélisation possible

Après la phase 1 :

- notifications/tâches peuvent avancer en parallèle du Gateway ;
- design du dashboard peut être prototypé sans connecteurs réels ;
- threat model et tests d’injection avancent en continu ;
- documentation d’auto-hébergement peut avancer avec le Gateway.

À ne pas paralléliser sans contrat stable :

- providers avant `ModelProvider` ;
- outils avant registre et Policy Engine ;
- skills avant permissions et sandbox ;
- automatisations avant audit et hooks.

## Priorités

- P0 : bloque un parcours principal ou une garantie de sécurité.
- P1 : nécessaire au MVP ou à la qualité d’usage.
- P2 : amélioration pouvant être reportée.

Une phase n’exige pas nécessairement toutes ses P2 pour être déclarée terminée. Les P0 doivent être fermées et les P1 restantes explicitement acceptées.

## Estimation indicative

Pour un développeur principal :

- phase 0 : 1–2 semaines ;
- phase 1 : 6–10 semaines ;
- phase 2 : 6–8 semaines ;
- phase 3 : 4–7 semaines ;
- phase 4 : 6–10 semaines ;
- phase 5 : 8–12 semaines ;
- finalisation phase 6 : 4–8 semaines.

Ces estimations supposent une validation régulière sur appareil réel et n’incluent pas les délais externes de publication ou de vérification OAuth.

## Mise à jour du plan

À la fin de chaque phase :

1. publier un rapport de validation ;
2. mettre à jour les contraintes confirmées ;
3. créer ou ajuster les issues de la phase suivante ;
4. supprimer les hypothèses invalidées ;
5. consigner les décisions d’architecture ;
6. réviser les estimations.
