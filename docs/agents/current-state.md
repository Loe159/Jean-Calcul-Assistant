# État réel du produit et registre des écarts

**OBSERVÉ au 2026-11-03**, base Git `7490e79f19b0ec78136db622d2588032cd5c0596`
(`fix: direct ChatGPT agent HTTP 400`). Audit statique ; aucune nouvelle validation Android
ou authentification réelle réalisée pour écrire ce document. Réévaluer après chaque mission.

## Existant

| Domaine | Implémentation observée | Preuve |
| --- | --- | --- |
| Android | minSdk 26, compile/target 35, JDK 17, Core/Power User | [build app](../../android/app/build.gradle.kts) |
| Assemblage | 15 projets Gradle, Kotlin/Compose/Hilt, pas de Gateway | [settings](../../settings.gradle.kts) |
| Domaine | Modèles/agents distincts, événements, politique, schémas | [carte des contrats](code-map.md) |
| Conversation | Orchestrateur modèle/agent, messages progressifs | [orchestrateur](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt) |
| Connexions | 4 adaptateurs directs, 2 backends agents via une factory | [factory](../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt) |
| Données | Room v2, migration 1→2, DataStore, Keystore | [base](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/JeanCalculDatabase.kt) |
| Outils | Volume et 7 outils MVP ; registre versionné, reçus | [schémas](../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/MvpToolSchemas.kt) |
| Voix | STT/TTS, overlay et processeur agent distinct | [processeur voix](../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceAgentCommandProcessor.kt) |
| UI | Design system Lumina, onboarding, conversations, réglages, audit | [navigation](../../android/app/src/main/java/fr/loevan/jeancalcul/app/MainAppContent.kt) |
| Compagnon | Python stdlib, Codex app-server, HTTP NDJSON v2 | [serveur](../../companion/codex_companion.py) |

## Écarts observés et risques à reproduire

| ID | Observation | Conséquence | Mission |
| --- | --- | --- | --- |
| GAP-01 | sendDraft ne route que l'agent ; sinon persistLocalDraft | Un modèle direct configuré ne produit pas de réponse depuis cette UI | JC-01, JC-03 |
| GAP-02 | Deux IDs actifs ; activateModel/activateAgent conservent l'autre | Sélection ambiguë, priorité implicite à l'agent | JC-01 |
| GAP-03 | Pas de Job, busy, stop ou retry dans les actions UI | Concurrence et absence d'annulation utilisateur | JC-02, JC-03 |
| GAP-04 | newConversation persiste puis le collecteur peut sélectionner le premier élément | Tester création et sélection initiale ensemble | JC-03 |
| GAP-05 | Erreur intitulée « Export impossible », enums anglais | Diagnostic trompeur et lisibilité faible | JC-07 |
| GAP-06 | Runtime : R2 automatique et préférence ALLOW synthétique | Le profil strict configuré n'est pas fidèlement appliqué | JC-04 |
| GAP-07 | Runtime : erreur pour CONFIRM/BIOMETRIC, sans suspension UI | Confirmation impossible jusqu'au reçu/résultat | JC-05 |
| GAP-08 | Voix : session neuve par demande, deltas accumulés en mémoire | Même factory ≠ même historique/orchestrateur texte-voix | JC-06 |
| GAP-09 | runCatching vocal capture aussi l'annulation | Interruption potentiellement transformée en erreur | JC-06 |
| GAP-10 | Catches orchestrateur centrés sur ProviderException ; écritures en contexte annulé | Une exception inattendue ou un EOF peut laisser STREAMING | JC-02 |
| GAP-11 | retryAgentResponse utilise un toolRuntime nul | Le réessai ne conserve pas les outils | JC-02, JC-05 |
| GAP-12 | retryContext accepte un ancien échec, historique non borné | Réessai susceptible d'inclure des tours futurs | JC-02 |
| GAP-13 | Compagnon : runs en mémoire, relâchés après terminal ; curseur Android conservé | « resume » ne prouve pas une reprise après redémarrage | JC-10 |
| GAP-14 | exportConversation sérialise directement textes/erreurs et métadonnées, sans appel de redaction | Un secret collé dans un texte peut être partagé en clair | JC-09 |
| GAP-15 | DataStore utilise preferencesDataStore et Mutex local ; Room sans invalidation multi-instance configurée | Cohérence app/assistant_session à établir, pas garantie par Hilt | JC-09, JC-06 |
| GAP-16 | nextMessageSequence lit MAX+1 séparément de saveMessageAndTouch | Deux producteurs peuvent réserver la même séquence | JC-09 |

Sources GAP-01 à 05 : [ViewModel conversation](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationViewModel.kt),
[actions](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreenActions.kt),
[écran](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreen.kt),
[réglages](../../android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsViewModel.kt).
GAP-06/07 : [runtime](../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt).
GAP-10/11/12 : [orchestrateur](../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt).
Sources GAP-14/16 : [repository](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/RoomConversationRepository.kt)
et [DAO](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/ConversationDao.kt).
GAP-15 : [DataStore](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/settings/DataStoreAssistantSettingsRepository.kt)
et [construction Room](../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/ConversationDataModule.kt).
Une lecture statique ne prouve pas la fréquence des risques sur appareil ; écrire le test de reproduction.

## Divergence d'authentification

[ADR 0016](../architecture/0016-codex-chatgpt-companion.md) décrit les identifiants ChatGPT
confinés au CLI PC et des outils encore absents. Le compagnon v2 expose désormais des outils.
[ADR 0017](../architecture/0017-chatgpt-plan-direct.md) annonce un accès Android direct à
Responses par abonnement ; ChatGptPlanAuthManager/AgentBackend existent dans le code.
Les deux chemins coexistent dans la factory. Le plan 1B reste centré sur le compagnon.

**À VALIDER** : disponibilité officielle du flux direct, éligibilité compte/modèle, scopes,
inférence réelle, expiration/révocation. MockWebServer ne valide aucun de ces faits.
Ne pas transformer les affirmations d'un ADR en garantie publique.
La [spec auth](../specs/03-backends-auth-and-protocol.md) définit les preuves et la récupération.

## Non livré comme produit

Pas de gateway, listener notifications, calendrier/email/dashboard/mémoire dans les projets
inclus. feature-tasks est un stockage simple, pas le produit tâches de phase 2.
Une permission énumérée ne prouve pas son application dans le runtime.
Power User est une variante, pas une automatisation d'accessibilité disponible.

Pour cette documentation, adb n'a listé aucun appareil. Aucun nouvel essai Samsung ni build
Android n'est revendiqué. Les [rapports historiques](../testing/device-matrix.md) restent datés
et ne qualifient pas automatiquement le commit courant. Voir [la validation](verification.md).
