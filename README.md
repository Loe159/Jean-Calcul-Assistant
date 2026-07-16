# Jean-Calcul Assistant

Assistant personnel Android open source, local-first et configurable.

Le projet vise une intégration comme assistant Android, une interaction vocale, des fournisseurs de modèles interchangeables, un backend Hermes optionnel et des modules personnels pour les notifications, tâches, calendrier, mails et dashboard.

## Démarrer le projet Android

Prérequis : JDK 17 et Android Studio Ladybug (ou une version plus récente) avec Android SDK Platform 35.

```bash
git clone https://github.com/Loe159/Jean-Calcul-Assistant.git
cd Jean-Calcul-Assistant
./gradlew assembleCoreDebug assemblePowerUserDebug
./gradlew test ktlintCheck detekt lintCoreDebug lintPowerUserDebug
```

Ouvre ensuite le dossier racine dans Android Studio. Le projet utilise le wrapper Gradle et le catalogue
`gradle/libs.versions.toml` : aucun Gradle installé globalement n’est nécessaire.

Les variantes `core` et `powerUser` sont deux binaires distincts. La variante Power User est uniquement
une frontière de distribution à ce stade : aucune accessibilité ni automatisation n’est encore implémentée.

### Modules initiaux

| Module | Responsabilité actuelle | Dépendances autorisées |
| --- | --- | --- |
| `app` | Point d’entrée Compose et assemblage des variantes | Tous les modules initiaux, jamais l’inverse |
| `assistant-service` | Future intégration `VoiceInteractionService` | `core-domain` uniquement |
| `assistant-session` | Future session assistant et UI de session | `core-domain`, `core-ui` |
| `core-domain` | Contrats Kotlin indépendants d’Android | Aucune dépendance Android |
| `core-data` | Future persistance et repositories | `core-domain` |
| `core-ui` | Fondations Compose partagées | Aucune couche métier ou data |
| `tool-bridge` | Future registre et exécution déterministe d’outils | `core-domain` |

Les services Android et la session ne sont volontairement pas déclarés à ce stade : leur implémentation
appartient aux issues suivantes. Les décisions d’architecture de la fondation sont consignées dans
[`docs/architecture/0001-android-foundation.md`](docs/architecture/0001-android-foundation.md).

## Commencer ici

- [`AGENTS.md`](AGENTS.md) — instructions obligatoires pour tout agent travaillant sur le dépôt.
- [`docs/plan/README.md`](docs/plan/README.md) — index du plan d’implémentation.
- [`docs/plan/01-system-architecture.md`](docs/plan/01-system-architecture.md) — architecture cible.
- [`docs/plan/04-roadmap-and-dependencies.md`](docs/plan/04-roadmap-and-dependencies.md) — ordre des phases et dépendances.
- [Backlog GitHub](../../issues) — tâches exécutables, priorités et critères d’acceptation.

## Développement actuel

- Phase 0 : validation technique de l’intégration assistant Android/Samsung — epic #7.
- Phase 1 : assistant vocal minimal, fournisseurs interchangeables et outils Android sécurisés — epic #17.

Les issues GitHub sont la source de vérité pour l’avancement. La documentation fournit le contexte, les contraintes et les détails nécessaires à l’exécution.
