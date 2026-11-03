# JC-03 — Raccorder le parcours texte aux modèles et agents

## Contrat de mission

- Issues parentes : [#40](https://github.com/Loe159/Jean-Calcul-Assistant/issues/40), [#43](https://github.com/Loe159/Jean-Calcul-Assistant/issues/43). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-01, JC-02.
- Skill : [$jc-runtime](../../../.agents/skills/jc-runtime/SKILL.md).
- Exigences : PROD-01, PROD-02, PROD-03, CONV-02, CONV-03, CONV-04, CONV-05, CONV-13, CONV-14, NET-06.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

sendDraft enregistre seulement le message si aucun agent n’est choisi. Les modèles directs et les options configurées ne sont pas reliés au bouton Envoyer ; les callbacks stop/retry sont absents.

Livrer : Un ViewModel raccordé à l’état effectif et au runtime, avec modèle/agent séparés, draft préservé, création/reprise, stop/retry et erreurs récupérables.

## Lecture minimale

- [00-product.md](../../specs/00-product.md)
- [01-conversation-runtime.md](../../specs/01-conversation-runtime.md)
- [03-backends-auth-and-protocol.md](../../specs/03-backends-auth-and-protocol.md)

Sources à inspecter, avec leurs tests de module :

- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationViewModel.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationViewModel.kt)
- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreenActions.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreenActions.kt)
- [android/app/src/main/java/fr/loevan/jeancalcul/app/MainActivity.kt](../../../android/app/src/main/java/fr/loevan/jeancalcul/app/MainActivity.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/OpenAiCompatibleProvider.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/OpenAiCompatibleProvider.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/anthropic/AnthropicProvider.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/anthropic/AnthropicProvider.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/openrouter/OpenRouterProvider.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/openrouter/OpenRouterProvider.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/ollama/OllamaProvider.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/ollama/OllamaProvider.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Vérifier les factories/configurations réseau existantes et construire un resolver de modèle dans la couche technique appropriée ; ne pas injecter un agent dans ModelProvider.
2. Router selon la sélection JC-01 ; charger connexion/secret et options du modèle. Sans profil, garder draft et exposer action de configuration sans fausse réponse.
3. Gérer espace Nouvelle vide et sélection stable ; premier envoi crée les sessions adaptées. Un changement de profil exige un nouveau fil explicite pour ce jalon.
4. Raccorder l’état de tour et les callbacks arrêter/réessayer ; préserver le draft suivant et les erreurs d’export distinctes.
5. Protéger suppression active avec stop/cleanup et confirmation host ; conserver export snapshot. Garder toute logique Android d’outil derrière son runtime.

## Critères d'acceptation

- [ ] Chaque kind de modèle direct rejoint son adaptateur et transmet les options ; l’agent rejoint uniquement AgentBackend.
- [ ] Aucun profil : zéro réseau, zéro faux assistant, draft intact et CTA réglages.
- [ ] Nouvelle reste sélectionnée, double clic ne double pas, le draft suivant survit à la réponse.
- [ ] Stop/retry atteignent le tour exact et les erreurs ne sont pas étiquetées export.
- [ ] Suppression d’un tour actif attend le cleanup ; aucune écriture ne le recrée.

## Vérification

Créer ConversationViewModelTest avec dépendances factices et dispatcher main contrôlé ; réutiliser les tests orchestrateur. Éviter d’exiger un vrai Context pour les seules décisions de routage.

```bash
./gradlew :feature-conversation:testDebugUnitTest :core-network:testDebugUnitTest
./gradlew assembleCoreDebug assemblePowerUserDebug
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Pas de redesign complet de l’écran ni de supposition sur l’accès réel ChatGPT ; JC-07 et JC-10 couvrent ces sujets.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. JC-07 peut utiliser les nouveaux états/actions ; fournir les fakes des modèles/agents à JC-06.
