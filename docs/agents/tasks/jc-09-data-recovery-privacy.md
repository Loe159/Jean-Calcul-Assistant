# JC-09 — Sécuriser persistance, récupération et exports

## Contrat de mission

- Issues parentes : [#40](https://github.com/Loe159/Jean-Calcul-Assistant/issues/40), [#44](https://github.com/Loe159/Jean-Calcul-Assistant/issues/44). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-02.
- Skill : [$jc-data](../../../.agents/skills/jc-data/SKILL.md).
- Exigences : DATA-01, DATA-02, DATA-03, DATA-04, DATA-05, DATA-06, DATA-07, DATA-08, DATA-09, DATA-10, CONV-12, CONV-13, QA-04.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Les producteurs et processus partagent un historique durable, mais la récupération STREAMING, l’allocation de séquences, les caches de sélection et la redaction d’export doivent être prouvés.

Livrer : Invariants de données testés, reprise des orphelins, suppression sans résurrection, exports expurgés et stratégie explicite de cohérence interprocessus.

## Lecture minimale

- [04-data-and-privacy.md](../../specs/04-data-and-privacy.md)
- [01-conversation-runtime.md](../../specs/01-conversation-runtime.md)

- [07-quality-and-acceptance.md](../../specs/07-quality-and-acceptance.md)

Sources à inspecter, avec leurs tests de module :

- [android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/JeanCalculDatabase.kt](../../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/JeanCalculDatabase.kt)
- [android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/RoomConversationRepository.kt](../../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/RoomConversationRepository.kt)
- [android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/ConversationDao.kt](../../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/conversation/ConversationDao.kt)
- [android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/settings/DataStoreAssistantSettingsRepository.kt](../../../android/core-data/src/main/kotlin/fr/loevan/jeancalcul/data/settings/DataStoreAssistantSettingsRepository.kt)
- [android/core-observability/src/main/kotlin/fr/loevan/jeancalcul/observability/RedactedAuditExporter.kt](../../../android/core-observability/src/main/kotlin/fr/loevan/jeancalcul/observability/RedactedAuditExporter.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Tester allocation de séquences et association des messages depuis les producteurs réels ; rendre atomiques les opérations qui en ont besoin.
2. Récupérer les messages sans propriétaire reprenable en INTERRUPTED, de façon idempotente.
3. Identifier l’accès DataStore/Room depuis app et assistant_session ; documenter puis tester le mécanisme de cohérence choisi, sans supposer que Singleton Hilt est global aux processus.
4. Tester cascade et exclusion des écrivains tardifs ; exporter un snapshot cohérent avec redaction des textes/erreurs/métadonnées sensibles.
5. Si schéma change, incrémenter version, ajouter migration et export sans réécrire anciens schémas ; tester chemins v1/v2. Conserver allowBackup=false.

## Critères d'acceptation

- [ ] Aucune séquence dupliquée et messages toujours associés au bon parent.
- [ ] Relance ne laisse pas de STREAMING abandonné ni ne démarre un deuxième run.
- [ ] Suppression active ne recrée rien ; export ne contient aucun marqueur de faux secret.
- [ ] Sélection/révocation cohérentes entre processus selon la stratégie explicitement testée.
- [ ] Les anciennes données sont conservées après toute migration nécessaire.

## Vérification

Étendre ConversationDatabaseTest, DataStoreAssistantSettingsRepositoryTest, AuditDatabaseTest et tests de redaction. Tester la base réelle en instrumentation ; fake repository seul insuffisant pour transactions/migrations.

```bash
./gradlew :core-security:testDebugUnitTest :core-observability:testDebugUnitTest :feature-conversation:testDebugUnitTest
./gradlew :core-data:connectedDebugAndroidTest
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Pas de chiffrement total ou synchronisation multi-appareils annoncé sans implémentation ; pas de fallback destructif.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Documenter le partage de données interprocessus avant JC-06 ; transmettre preuves de migration/export à JC-11.
