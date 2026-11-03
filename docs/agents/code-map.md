# Carte du code et frontières

**OBSERVÉ** sur la base documentaire indiquée dans [l’état réel](current-state.md).
La structure cible du plan peut inclure des modules absents ; seules les inclusions de
[settings.gradle.kts](../../settings.gradle.kts) prouvent un projet Gradle actuel.

## Projets Gradle

| Projet / sources | Responsabilité | Dépendances de projet déclarées |
| --- | --- | --- |
| [:app](../../android/app/) | Assemblage Hilt, activité, navigation, onboarding, audit et partage Android | `:assistant-service`, `:assistant-session`, `:baseline-profile`, `:core-data`, `:core-domain`, `:core-network`, `:core-observability`, `:core-security`, `:core-ui`, `:feature-conversation`, `:feature-settings`, `:feature-tasks`, `:feature-voice`, `:tool-bridge` |
| [:assistant-service](../../android/assistant-service/) | Rôle assistant et service minimal ; aucun runtime lourd au démarrage | `:core-domain`, `:core-observability` |
| [:assistant-session](../../android/assistant-session/) | Fenêtre transparente, lifecycle, orchestration vocale et confirmation host | `:core-domain`, `:core-observability`, `:core-ui`, `:feature-conversation`, `:feature-voice`, `:tool-bridge` |
| [:core-domain](../../android/core-domain/) | Contrats JVM, états, sélection, outils et PolicyEngine | Aucune |
| [:core-data](../../android/core-data/) | Room v2, DAO/repositories et DataStore ; migrations et cohérence de données | `:core-domain` |
| [:core-network](../../android/core-network/) | Adaptateurs directs, backends agents, protocole et auth | `:core-domain`, `:core-security` |
| [:core-observability](../../android/core-observability/) | Audit persistant, redaction/export et traces de performance | `:core-domain`, `:core-security` |
| [:core-security](../../android/core-security/) | Keystore, SecretStore et redaction des secrets | `:core-domain` |
| [:core-ui](../../android/core-ui/) | Tokens, thème, composants et previews sans logique métier | Aucune |
| [:feature-conversation](../../android/feature-conversation/) | UI/état conversation, orchestrateur et recorder vocal actuel | `:core-data`, `:core-domain`, `:core-observability`, `:core-ui`, `:tool-bridge` |
| [:feature-settings](../../android/feature-settings/) | UI/état des connexions, profils, voix et apparence | `:core-data`, `:core-domain`, `:core-network`, `:core-security`, `:core-ui` |
| [:feature-tasks](../../android/feature-tasks/) | Stockage simple des tâches locales MVP | `:core-data`, `:core-domain` |
| [:feature-voice](../../android/feature-voice/) | STT/TTS Android, focus/route/amplitude/VAD et pipeline | `:core-domain`, `:core-observability` |
| [:tool-bridge](../../android/tool-bridge/) | Registre, runtime de politique et adaptateurs Android | `:core-domain`, `:feature-tasks` |
| [:baseline-profile](../../android/baseline-profile/) | Scénarios de parcours critiques, profils et macrobenchmarks | Aucune |

Les dépendances ci-dessus incluent les configurations de test/profil : elles ne décrivent pas
toutes une dépendance runtime. Les lire dans le build concerné avant toute conclusion de frontière.

## Fichiers de décision et points d’entrée

| Question | Sources à inspecter |
| --- | --- |
| Contrats des modèles/agents | [ModelProvider.kt](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ModelProvider.kt) ; [AgentBackend.kt](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/AgentBackend.kt) ; [ProviderContracts.kt](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ProviderContracts.kt) |
| Sélection/configuration | [AssistantSettings.kt](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/AssistantSettings.kt) ; [SettingsViewModel.kt](../../android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsViewModel.kt) |
| Tour texte | [ConversationViewModel.kt](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationViewModel.kt) ; [ConversationOrchestrator.kt](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt) |
| Exécution/confirmation | [LocalAgentToolRuntime.kt](../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt) ; [ToolRegistry.kt](../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/ToolRegistry.kt) ; [PolicyEngine.kt](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/PolicyEngine.kt) |
| Voix | [VoiceSessionController.kt](../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceSessionController.kt) ; [VoiceAgentCommandProcessor.kt](../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceAgentCommandProcessor.kt) ; [VoicePipeline.kt](../../android/feature-voice/src/main/kotlin/fr/loevan/jeancalcul/voice/VoicePipeline.kt) |
| Factory agents/auth | [CodexCompanionAgentBackend.kt](../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt) ; [ChatGptPlanAuthManager.kt](../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/ChatGptPlanAuthManager.kt) |
| Données et migrations | [JeanCalculDatabase.kt](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/JeanCalculDatabase.kt) ; [ConversationDao.kt](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/ConversationDao.kt) ; [DataStoreAssistantSettingsRepository.kt](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/settings/DataStoreAssistantSettingsRepository.kt) |
| Écrans réels/tokens | [MainAppContent.kt](../../android/app/src/main/java/fr/loevan/jeancalcul/app/MainAppContent.kt) ; [DesignTokens.kt](../../android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/DesignTokens.kt) |
| Compagnon et CI | [codex_companion.py](../../companion/codex_companion.py) ; [android.yml](../../.github/workflows/android.yml) |

## Tests présents et lacunes

Les sources test et androidTest sont séparées. À la base auditée, la feature conversation a
ConversationOrchestratorTest mais pas encore ConversationViewModelTest ; l’existence de tests
du premier ne qualifie pas le routage du second. Les suites network utilisent des serveurs factices.

| Module | Tests JVM présents | Sources instrumentées présentes |
| --- | ---: | ---: |
| :app | 1 fichiers | 0 fichiers |
| :assistant-service | 0 fichiers | 0 fichiers |
| :assistant-session | 7 fichiers | 2 fichiers |
| :core-domain | 14 fichiers | 0 fichiers |
| :core-data | 0 fichiers | 3 fichiers |
| :core-network | 8 fichiers | 0 fichiers |
| :core-observability | 2 fichiers | 0 fichiers |
| :core-security | 2 fichiers | 1 fichiers |
| :core-ui | 1 fichiers | 1 fichiers |
| :feature-conversation | 1 fichiers | 0 fichiers |
| :feature-settings | 1 fichiers | 0 fichiers |
| :feature-tasks | 0 fichiers | 1 fichiers |
| :feature-voice | 1 fichiers | 0 fichiers |
| :tool-bridge | 4 fichiers | 2 fichiers |
| :baseline-profile | 0 fichiers | 0 fichiers |

Ces nombres sont un inventaire de fichiers au commit de base, pas un taux de couverture ni un résultat
de tests. [La matrice](verification.md) indique les commandes adaptées et les preuves qu’elles donnent.

## Frontières à préserver

- core-domain ne connaît ni Context, ni OkHttp, ni Room, ni Compose.
- core-ui reçoit état/callbacks ; ses composants ne créent pas de permission ou décision métier.
- tool-bridge connaît les outils/adaptateurs et reçus, pas la présentation de confirmation.
- core-network implémente les contrats sans exécuter une API Android demandée par le modèle.
- app et assistant-session sont des hosts distincts ; leurs singletons/caches ne sont pas globaux aux processus.
- companion utilise le CLI et son auth ; il ne devient pas un shell général ou le Gateway futur.

Lire le build et les instructions locales avant de déplacer une responsabilité ; un diagramme de
cible ne justifie pas une dépendance inverse. Les ajustements indispensables doivent rester testés.
