# JC-05 — Faire aboutir les confirmations jusqu’au résultat outil

## Contrat de mission

- Issues parentes : [#44](https://github.com/Loe159/Jean-Calcul-Assistant/issues/44). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-02, JC-04.
- Skill : [$jc-tools-policy](../../../.agents/skills/jc-tools-policy/SKILL.md).
- Exigences : POL-04, POL-05, POL-06, POL-07, POL-08, POL-09, POL-10, POL-11, POL-12, CONV-09, CONV-10, UI-09.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Les décisions CONFIRM/BIOMETRIC retournent actuellement une erreur ; elles ne suspendent pas le run en attendant une décision Android avec reçu valide.

Livrer : Une coordination d’approbation suspendable, présentée par le host, corrélée au bon outil/run, avec refus/expiration/annulation et retour au backend.

## Lecture minimale

- [01-conversation-runtime.md](../../specs/01-conversation-runtime.md)
- [02-tools-policy-and-approvals.md](../../specs/02-tools-policy-and-approvals.md)
- [06-ux-and-design.md](../../specs/06-ux-and-design.md)

Sources à inspecter, avec leurs tests de module :

- [android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt](../../../android/tool-bridge/src/main/kotlin/fr/loevan/jeancalcul/toolbridge/LocalAgentToolRuntime.kt)
- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/PolicyEngine.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/PolicyEngine.kt)
- [android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/ActionComponents.kt](../../../android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/ActionComponents.kt)
- [android/app/src/main/java/fr/loevan/jeancalcul/app/MainActivity.kt](../../../android/app/src/main/java/fr/loevan/jeancalcul/app/MainActivity.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Définir un état de proposition en attente et un canal de décision possédés par le runtime. Adapter le contrat execute si nécessaire sans mettre Compose dans tool-bridge/domain.
2. Présenter action/arguments/raison/expiration exacts et boutons Confirmer/Refuser. La biométrie est un effet du host et ne se remplace pas par un booléen fabriqué.
3. Sur décision, réévaluer contexte puis faire émettre/valider le reçu existant ; exécuter au plus une fois. Résoudre aussi refus, fermeture, timeout, permission révoquée et verrouillage.
4. Renvoyer ToolResult au callId/run exact et auditer toutes les issues. Retry conserve ce chemin et refuse les effets incertains.
5. Tester deux décisions rapides et événement tardif ; aucun bouton ne peut confirmer une proposition remplacée ou expirée.

## Critères d'acceptation

- [ ] Lampe STRICT : zéro effet en attente, un effet après confirmation valide.
- [ ] Refus/fermeture/expiration/cancel : zéro effet et attente toujours libérée.
- [ ] Arguments modifiés ou contexte révoqué : reçu inutilisable.
- [ ] Réponse finale backend utilise le résultat réel ou le refus ; pas de blocage après rejet.
- [ ] Biométrie indisponible : action non exécutée et ouverture/déverrouillage proposé.

## Vérification

Tests du coordinateur avec horloge/scheduler, policy/registry et fake AgentToolResultSink ; instrumentation du host pour la présentation et biométrie réelle lors de JC-12.

```bash
./gradlew :tool-bridge:testDebugUnitTest :feature-conversation:testDebugUnitTest :core-domain:test
./gradlew assembleCoreDebug assemblePowerUserDebug
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Ne pas appeler approveTool distant pour simuler la confirmation locale ; ne pas changer le transport pour contourner un timeout.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Transmettre état/callbacks de confirmation à JC-06 et JC-07 avec tests de refus/expiration.
