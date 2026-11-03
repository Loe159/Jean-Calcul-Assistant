# JC-02 — Garantir la fin, l’annulation et le réessai des tours

## Contrat de mission

- Issues parentes : [#40](https://github.com/Loe159/Jean-Calcul-Assistant/issues/40), [#43](https://github.com/Loe159/Jean-Calcul-Assistant/issues/43). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-01.
- Skill : [$jc-runtime](../../../.agents/skills/jc-runtime/SKILL.md).
- Exigences : CONV-05, CONV-06, CONV-07, CONV-08, CONV-09, CONV-10, CONV-11, CONV-12, QA-02, QA-03.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

L’orchestrateur persiste les deltas mais ne protège pas tous les cas EOF/exception/cancel. Le réessai agent perd le runtime outils, et un ancien échec peut recevoir un contexte contenant des tours ultérieurs.

Livrer : Un cycle de tour testable, corrélé et terminal sur tous les chemins ; retry du dernier tour sans doublon et exclusion des exécutions concurrentes du runtime MVP.

## Lecture minimale

- [01-conversation-runtime.md](../../specs/01-conversation-runtime.md)

- [07-quality-and-acceptance.md](../../specs/07-quality-and-acceptance.md)

Sources à inspecter, avec leurs tests de module :

- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationOrchestrator.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/Conversation.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/Conversation.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ConversationRepository.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ConversationRepository.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ProviderContracts.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ProviderContracts.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Écrire d’abord des fake streams contrôlables : suspension avant session, deux deltas, échec, EOF, exception inattendue et émission tardive. Observer messages et appels cancel.
2. Réserver le tour avant suspension ; identifier le propriétaire par requestId/conversationId, sans le confondre avec la conversation affichée. Préparer les messages une seule fois après validation.
3. Traiter EOF, Completed vide, erreur normalisée et erreur inattendue ; préserver le texte partiel et expurger l’erreur. Définir un terminal unique et ignorer les événements incohérents/tardifs.
4. Annuler Job et backend exact ; cleanup local borné non annulable, même si le cancel réseau échoue. Propager CancellationException.
5. Borner le retry au dernier tour échoué/interrompu ; réutiliser les messages, nouveau requestId, contexte jusqu’au bon utilisateur et runtime outils conservé. Ne pas répéter un effet d’état inconnu.

## Critères d'acceptation

- [ ] Deltas visibles sur un seul assistant ; un terminal cohérent est persisté.
- [ ] EOF et exception inattendue ne laissent aucun STREAMING orphelin.
- [ ] Annuler avant premier token ou pendant stream ferme le travail et permet un nouveau tour.
- [ ] Retry conserve le nombre/IDs des messages et les outils ; ancien tour non terminal du fil refusé.
- [ ] Double envoi et événements d’un autre requestId ne produisent ni second run ni texte croisé.

## Vérification

Étendre ConversationOrchestratorTest avec kotlinx-coroutines-test et fake repository. Contrôler les événements intermédiaires, pas seulement la valeur finale.

```bash
./gradlew :feature-conversation:testDebugUnitTest :core-domain:test
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Ne pas changer le wire compagnon, la politique des outils ou introduire des jobs persistants distants.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Transmettre les méthodes/états du runtime à JC-03, et le contrat d’attente/annulation à JC-05.
