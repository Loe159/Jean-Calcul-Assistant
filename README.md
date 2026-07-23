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

### Modules Android

| Module | Responsabilité actuelle | Dépendances autorisées |
| --- | --- | --- |
| `app` | Point d’entrée Compose et assemblage des variantes | Tous les modules nécessaires à l’application, jamais l’inverse |
| `assistant-service` | Service assistant Android léger et métadonnées système | `core-domain`, `core-observability` ; jamais Room ni réseau |
| `assistant-session` | Session Compose transparente isolée ; cycle de fermeture et fallback visuel | Domaine, UI, voix, conversation et pont d’outils |
| `core-domain` | Contrats Kotlin indépendants d’Android | Aucune dépendance Android |
| `core-data` | Fondation Room/DataStore et futurs repositories | `core-domain` |
| `core-network` | Fondation HTTP/streaming des futurs fournisseurs | `core-domain`, `core-security` |
| `core-observability` | Traces et budgets de performance partagés | Aucune couche fonctionnelle |
| `core-security` | Fondation Keystore, biométrie et expurgation | `core-domain` |
| `core-ui` | Fondations Compose partagées | Aucune couche métier ou data |
| `feature-conversation` | Frontière des conversations persistées et de leur UI | Domaine, data et UI |
| `feature-settings` | Frontière des écrans et profils de configuration | Domaine, data, réseau, sécurité et UI |
| `feature-tasks` | Frontière des tâches locales | Domaine et data |
| `feature-voice` | Frontière du pipeline vocal Android | Domaine et observabilité |
| `tool-bridge` | Registre et exécution déterministe d’outils | Domaine et fonctionnalités locales explicitement exposées |

Le rôle assistant est configuré par l’onboarding : il propose la demande système puis un accès de secours
aux paramètres de saisie vocale. Le service reste minimal ; la session transparente est isolée et la
reconnaissance vocale sera ajoutée dans son issue dédiée. Les décisions sont consignées dans
[`docs/architecture/0001-android-foundation.md`](docs/architecture/0001-android-foundation.md) et
[`docs/architecture/0002-android-assistant-role.md`](docs/architecture/0002-android-assistant-role.md),
[`docs/architecture/0003-transparent-assistant-session.md`](docs/architecture/0003-transparent-assistant-session.md)
et [`docs/architecture/0006-phase-1-module-foundation.md`](docs/architecture/0006-phase-1-module-foundation.md).

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
