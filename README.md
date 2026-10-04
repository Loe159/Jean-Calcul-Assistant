# Jean Calcul Assistant

Assistant personnel Android open source, local-first et configurable.

Jean Calcul peut remplacer l'assistant système Android, être invoqué depuis le bouton Power, dialoguer en voix ou en texte, utiliser plusieurs fournisseurs de modèles et exécuter des outils Android derrière un registre versionné, un moteur de politique et un journal d'audit.

## État du projet

- **Phase 0 terminée** : intégration assistant Android/Samsung, invocation Power, session transparente et premier parcours vocal validés.
- **Phase 1 implémentée** : design system, voix, fournisseurs, conversations locales, outils, politiques, secrets, paramètres et audit sont présents. L'epic #17 reste ouverte uniquement pour quatre validations physiques explicitement reportées.
- **Phase 1B active** : l'epic #39 raccorde maintenant l'interface à une vraie boucle Codex authentifiée via ChatGPT, avec appairage privé et appels d'outils de bout en bout.

Les issues GitHub sont la source de vérité pour l'avancement.

## Compiler l'application

Prérequis : JDK 17 et Android SDK Platform 35.

```bash
git clone https://github.com/Loe159/Jean-Calcul-Assistant.git
cd Jean-Calcul-Assistant
./gradlew assembleCoreDebug assemblePowerUserDebug
./gradlew test ktlintCheck detekt lintCoreDebug lintPowerUserDebug
```

Les APKs de debug sont également publiés comme artefacts par la CI GitHub Actions sur chaque push vers `main`.

## Utiliser Codex avec son abonnement ChatGPT

Jean Calcul peut utiliser Codex sans clé API OpenAI grâce au compagnon local inclus dans `companion/`. L'authentification ChatGPT reste entièrement dans le CLI Codex sur le PC.

```bash
python3 companion/codex_companion.py login
python3 companion/codex_companion.py serve
adb reverse tcp:43120 tcp:43120
```

Le guide complet de configuration Android et les garanties de sécurité sont dans [`companion/README.md`](companion/README.md).

## Architecture

Le projet est un monorepo Android Kotlin/Compose. Les principaux modules sont :

| Module | Rôle |
| --- | --- |
| `app` | Point d'entrée, navigation et assemblage des variantes |
| `assistant-service` | Intégration au rôle d'assistant Android |
| `assistant-session` | Session transparente, orchestration de l'invocation et UI assistant |
| `core-domain` | Contrats métier indépendants d'Android |
| `core-data` | Room, DataStore, conversations, paramètres et audit |
| `core-network` | Fournisseurs de modèles et transport réseau |
| `core-security` | Keystore, secrets et expurgation |
| `core-ui` | Design system Compose |
| `feature-conversation` | Conversation et streaming |
| `feature-settings` | Configuration des profils |
| `feature-voice` | Pipeline STT/TTS et audio |
| `feature-tasks` | Tâches locales |
| `tool-bridge` | Registre et exécution contrôlée des outils Android |

Deux variantes existent : `core` et `powerUser`. La variante Power User reste une frontière de distribution ; aucune automatisation intrusive n'y est activée par défaut.

## Contribuer

Avant toute modification, lire :

- [`AGENTS.md`](AGENTS.md) pour les règles de contribution des agents ;
- [`docs/plan/README.md`](docs/plan/README.md) pour la roadmap ;
- l'issue GitHub concernée et ses critères d'acceptation.

Les décisions d'architecture sont documentées dans [`docs/architecture/`](docs/architecture/) et les validations appareil dans [`docs/testing/`](docs/testing/) et [`docs/observability/`](docs/observability/).
