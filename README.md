# Jean Calcul Assistant

Assistant personnel Android open source, local-first et configurable.

Jean Calcul peut remplacer l'assistant système Android, être invoqué depuis le bouton Power, dialoguer en voix ou en texte, utiliser plusieurs fournisseurs de modèles et exécuter des outils Android derrière un registre versionné, un moteur de politique et un journal d'audit.

## Documentation pour les agents IA

Commencer par [AGENTS.md](AGENTS.md) et le [portail de délégation](docs/agents/README.md).
Le dossier contient un audit daté de l'existant, des spécifications avec exigences identifiées,
douze missions guidées et sept skills de développement portables. Le [prompt prêt à transmettre](docs/agents/templates/delegation.md)
permet de confier JC-01 à un agent sans lui demander de reconstruire tout le contexte.

```bash
python3 scripts/check_agent_docs.py
python3 scripts/check_agent_docs.py --task JC-01
```

## État du projet

- **Phase 0 terminée** : intégration assistant Android/Samsung, invocation Power, session transparente et premier parcours vocal validés.
- **Socle de phase 1 présent** : design system, voix, fournisseurs, stockage, outils, politiques, secrets, paramètres et audit. Leur présence ne qualifie pas le parcours intégré : voir [les écarts observés](docs/agents/current-state.md). #17 conserve des validations physiques ouvertes.
- **Remise en état / phase 1B active** : #39 couvre le raccordement réel, les confirmations, la parité voix/texte et la validation Samsung. Deux chemins ChatGPT coexistent dans le code ; leur [compatibilité et niveau de preuve](docs/specs/03-backends-auth-and-protocol.md) doivent être distingués.

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

Le chemin recommandé est entièrement direct : ouvrez **Configuration > Fournisseurs**, puis **Continuer avec ChatGPT**. Jean Calcul lance l'authentification OpenAI dans le navigateur, conserve les jetons dans Android Keystore et utilise ensuite l'API Responses directement avec le quota de l'abonnement ChatGPT/Codex.

Aucune clé API, aucun PC et aucun serveur Companion ne sont nécessaires. Les demandes d'actions du modèle continuent de passer par le registre d'outils Android, le Policy Engine et le journal d'audit avant exécution.

Le Companion historique reste disponible uniquement comme solution de compatibilité pour les anciennes configurations. Voir [l'architecture du backend direct](docs/architecture/0017-chatgpt-plan-direct.md).

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
