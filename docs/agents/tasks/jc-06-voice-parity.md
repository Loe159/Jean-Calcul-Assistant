# JC-06 — Unifier les tours texte et voix et libérer les ressources

## Contrat de mission

- Issues parentes : [#45](https://github.com/Loe159/Jean-Calcul-Assistant/issues/45). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-03, JC-05, JC-09.
- Skill : [$jc-voice](../../../.agents/skills/jc-voice/SKILL.md).
- Exigences : VOICE-01, VOICE-02, VOICE-03, VOICE-04, VOICE-05, VOICE-06, VOICE-07, VOICE-08, VOICE-09, VOICE-10, DATA-10.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

La voix utilise la même factory mais un autre chemin de run et d’historique. Elle accumule la réponse et capture potentiellement CancellationException comme une erreur.

Livrer : La transcription finale rejoint le runtime commun et la même conversation ; capture/TTS/réseau et attente sont libérés à l’arrêt, sans doublon de recorder.

## Lecture minimale

- [05-voice-and-android-lifecycle.md](../../specs/05-voice-and-android-lifecycle.md)
- [04-data-and-privacy.md](../../specs/04-data-and-privacy.md)

Sources à inspecter, avec leurs tests de module :

- [android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceAgentCommandProcessor.kt](../../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceAgentCommandProcessor.kt)
- [android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceSessionController.kt](../../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/VoiceSessionController.kt)
- [android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/JeanCalculVoiceInteractionSession.kt](../../../android/assistant-session/src/main/kotlin/fr/loevan/jeancalcul/assistant/session/JeanCalculVoiceInteractionSession.kt)
- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/VoiceConversationRecorder.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/VoiceConversationRecorder.kt)
- [android/feature-voice/src/main/kotlin/fr/loevan/jeancalcul/voice/VoicePipeline.kt](../../../android/feature-voice/src/main/kotlin/fr/loevan/jeancalcul/voice/VoicePipeline.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Cartographier propriétaires de session/processus, recorder et accès données ; définir comment le runtime effectif coordonne app et assistant_session, selon DATA-10.
2. Router une transcription finale une seule fois ; conserver l’origine voix et les références de conversation/session. Les résultats partiels n’envoient rien.
3. Retirer les écritures redondantes du recorder dans le chemin commun sans perdre la voie locale déterministe.
4. Afficher le streaming et la confirmation commune ; TTS peut attendre le terminal. Un oui ambigu n’autorise rien et ne remplace pas la biométrie.
5. Propager annulation et libérer micro/audio focus/TTS/backend au stop, appel entrant, focus perdu et fermeture. Tester les ressources possédées.

## Critères d'acceptation

- [ ] Demande générale reçoit une réponse du backend, pas une erreur de vocabulaire volume.
- [ ] Texte puis voix gardent le contexte et un seul historique, avec outil/politique/audit identiques.
- [ ] Aucun réseau ni outil déclenché par un partiel ou une fin recognizer dupliquée.
- [ ] Arrêt et refus libèrent tout ; aucun son ou delta tardif ; mode volume local conservé.

## Vérification

Étendre VoiceSessionControllerTest, VoiceVolumeEndToEndScenarioTest et ajouter la parité avec un fake backend et les mêmes fixtures de politique ; conserver tests instrumentés et rapport Samsung ouverts jusqu’à essai réel.

```bash
./gradlew :assistant-session:testDebugUnitTest :feature-voice:testDebugUnitTest :feature-conversation:testDebugUnitTest
./gradlew assembleCoreDebug assemblePowerUserDebug
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Pas d’écoute continue, d’enregistrement brut, d’accès notification ou de dépendance lourde dans VoiceInteractionService.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Transmettre les cas audio restant à jouer physiquement à JC-12.
