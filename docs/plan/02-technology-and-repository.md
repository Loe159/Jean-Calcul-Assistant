# Technologies et organisation du dépôt

## Application Android

Technologies retenues :

- Kotlin ;
- Jetpack Compose ;
- Material 3 comme fondation, avec design system propre ;
- Coroutines et Flow ;
- Hilt ;
- Room ;
- DataStore ;
- WorkManager ;
- Kotlin Serialization ;
- OkHttp ou Ktor Client ;
- Android Keystore ;
- BiometricPrompt ;
- Media3 ;
- JUnit, Turbine, MockK et tests Compose ;
- Macrobenchmark et Baseline Profiles.

## Agent Gateway

Technologies prévues pour la phase 3 :

- Python 3.12+ ;
- FastAPI ;
- Pydantic ;
- asyncio ;
- WebSocket ;
- SQLAlchemy/SQLModel ;
- SQLite en installation personnelle ;
- PostgreSQL en déploiement plus important ;
- Alembic ;
- Docker Compose ;
- `uv` ;
- Pytest, Ruff et mypy ;
- OpenTelemetry.

## Contrats

- OpenAPI pour les endpoints REST ;
- JSON Schema pour les outils ;
- protocole WebSocket versionné ;
- UUID pour les sessions, messages, jobs et actions ;
- horodatage UTC ;
- clés d’idempotence ;
- numéros de séquence pour la reprise de flux.

## Structure cible

```text
Jean-Calcul-Assistant/
├── AGENTS.md
├── android/
│   ├── app/
│   ├── assistant-service/
│   ├── assistant-session/
│   ├── core-domain/
│   ├── core-data/
│   ├── core-network/
│   ├── core-security/
│   ├── core-ui/
│   ├── feature-conversation/
│   ├── feature-voice/
│   ├── feature-notifications/
│   ├── feature-tasks/
│   ├── feature-calendar/
│   ├── feature-email/
│   ├── feature-dashboard/
│   ├── feature-settings/
│   ├── tool-bridge/
│   └── automation-accessibility/
├── gateway/
│   ├── api/
│   ├── agents/
│   ├── providers/
│   ├── tools/
│   ├── jobs/
│   ├── security/
│   ├── storage/
│   └── integrations/
├── contracts/
│   ├── openapi/
│   ├── websocket/
│   ├── tools/
│   └── skills/
├── skills/
├── infra/
├── tests/
└── docs/
    ├── plan/
    ├── architecture/
    ├── testing/
    └── observability/
```

La structure doit être créée progressivement. Ne pas ajouter tous les modules vides dès le départ si la phase active n’en a pas besoin.

## Variantes Android

### Core

- API Android officielles ;
- aucune automatisation générale par accessibilité ;
- compatible avec une distribution classique ;
- confirmations strictes.

### Power User

- service d’accessibilité optionnel ;
- distribution directe ou F-Droid ;
- automatisations avancées ;
- avertissements et configuration explicites.

Les fonctionnalités communes doivent vivre dans des modules partagés. Le code Power User ne doit pas contaminer le binaire Core.

## Conventions de code Android

- UI unidirectionnelle ;
- état exposé par `StateFlow` ;
- effets externes séparés de la réduction d’état ;
- interfaces dans le domaine, implémentations dans les couches techniques ;
- pas d’appel réseau sur le thread principal ;
- pas de `Context` Android dans les modèles de domaine ;
- migrations Room obligatoires après la première release ;
- logs structurés et expurgés.

## Conventions Gateway

- modèles Pydantic stricts ;
- propriétés inconnues refusées pour les actions ;
- exceptions externes normalisées ;
- aucun secret dans les logs ;
- jobs longs persistés ;
- endpoints de santé séparés : liveness, readiness, version ;
- migrations testées avant publication.

## Qualité et CI

Android :

- compilation des variantes ;
- tests unitaires ;
- Android Lint ;
- Detekt ;
- ktlint ;
- tests instrumentés ciblés ;
- analyse de dépendances ;
- génération d’artefacts de test.

Gateway :

- Ruff ;
- mypy ;
- Pytest ;
- tests d’intégration ;
- scan de l’image ;
- SBOM.

## Documentation

- `AGENTS.md` : protocole de travail.
- `docs/plan/` : contexte et cible.
- issues GitHub : unités de travail.
- `docs/architecture/` : décisions d’architecture prises pendant l’implémentation.
- `docs/testing/` : matrices et procédures manuelles.
- `docs/observability/` : budgets et mesures.

## Gestion des décisions

Lorsqu’une décision importante diverge du plan initial :

1. documenter le problème ;
2. comparer les options ;
3. consigner la décision dans `docs/architecture/` ;
4. mettre à jour le document de phase ;
5. adapter les issues concernées.
