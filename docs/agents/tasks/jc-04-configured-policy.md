# JC-04 — Appliquer les permissions et politiques réellement configurées

## Contrat de mission

- Issues parentes : [#44](https://github.com/Loe159/Jean-Calcul-Assistant/issues/44). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-01.
- Skill : [$jc-tools-policy](../../../.agents/skills/jc-tools-policy/SKILL.md).
- Exigences : POL-01, POL-02, POL-03, POL-10, POL-11, POL-12.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

LocalAgentToolRuntime fabrique une préférence ALLOW et autorise les R2 automatiquement au lieu d’appliquer le profil configuré ; les permissions déclarées ne suffisent pas à prouver leur effet.

Livrer : Un contexte de politique issu de la configuration effective, un catalogue filtré cohérent avec l’exécution et une traçabilité des refus.

## Lecture minimale

- [02-tools-policy-and-approvals.md](../../specs/02-tools-policy-and-approvals.md)

Sources à inspecter, avec leurs tests de module :

- [android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt](../../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt)
- [android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/ToolRegistry.kt](../../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/ToolRegistry.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/PolicyEngine.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/PolicyEngine.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/AssistantSettings.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/AssistantSettings.kt)
- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationViewModel.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationViewModel.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Reproduire par un test la lampe exécutée en STRICT sans confirmation ; utiliser un compteur adaptateur et une horloge contrôlée.
2. Passer au runtime les données nécessaires du profil réel, pas seulement un profileId. Définir le mapping minimal POL-03 et le tester.
3. Retirer ALLOW synthétique et flags permissifs ; conserver les planchers de risque existants. Une règle utilisateur ne descend pas sous le plancher.
4. Filtrer le catalogue annoncé puis revérifier disponibilité/permission/verrouillage à l’exécution. Auditer refus et inconnus sans arguments sensibles.

## Critères d'acceptation

- [ ] STRICT + lampe ne touche pas l’adaptateur avant confirmation.
- [ ] Sans DEVICE_BASIC_CONTROL ou TASKS_WRITE, les outils correspondants sont absents/refusés.
- [ ] Permission révoquée après annonce et lockscreen restent bloquants.
- [ ] Batterie/heure R0 restent utilisables selon leur définition ; R4/R5 ne sont pas affaiblis.

## Vérification

Étendre PolicyEngineTest, MvpToolPolicyTest, ToolRegistryTest et ajouter des tests ciblés LocalAgentToolRuntime. Tester texte/voix comme origines distinctes.

```bash
./gradlew :core-domain:test :tool-bridge:testDebugUnitTest
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Ne pas baisser les niveaux de risque ; l’UI de confirmation est JC-05, pas un motif pour retourner ALLOW.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. JC-05 reçoit une vraie décision CONFIRM/BIOMETRIC et ses raisons ; noter le mapping de permissions appliqué.
