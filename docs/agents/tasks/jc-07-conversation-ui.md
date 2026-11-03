# JC-07 — Recomposer conversation et navigation pour un usage Android

## Contrat de mission

- Issues parentes : [#40](https://github.com/Loe159/Jean-Calcul-Assistant/issues/40), [#39](https://github.com/Loe159/Jean-Calcul-Assistant/issues/39). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-03, JC-05.
- Skill : [$jc-compose-ui](../../../.agents/skills/jc-compose-ui/SKILL.md).
- Exigences : UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07, UI-08, UI-09, UI-12, PROD-04, PROD-05, CONV-13.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

L’écran empile chips, actions et export/suppression permanents ; le composeur, les erreurs et la navigation ont des contraintes de taille/padding peu adaptées aux vrais états.

Livrer : Un écran hiérarchisé et accessible, avec destination visible, historique, messages, composeur IME, stop/retry et menu secondaire ; navigation par taille.

## Lecture minimale

- [06-ux-and-design.md](../../specs/06-ux-and-design.md)
- [01-conversation-runtime.md](../../specs/01-conversation-runtime.md)

- [00-product.md](../../specs/00-product.md)

Sources à inspecter, avec leurs tests de module :

- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreen.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreen.kt)
- [android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreenActions.kt](../../../android/feature-conversation/src/main/kotlin/fr/loevan/jeancalcul/feature/conversation/ConversationScreenActions.kt)
- [android/app/src/main/java/fr/loevan/jeancalcul/app/MainAppContent.kt](../../../android/app/src/main/java/fr/loevan/jeancalcul/app/MainAppContent.kt)
- [android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/ConversationComponents.kt](../../../android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/ConversationComponents.kt)
- [android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/SupportComponents.kt](../../../android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/SupportComponents.kt)
- [android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/DesignTokens.kt](../../../android/core-ui/src/main/kotlin/fr/loevan/jeancalcul/ui/DesignTokens.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Réutiliser les états JC-03/05 ; poser le layout en trois zones sans modifier leur sémantique.
2. Rendre les messages flexibles et scrollables ; autoscroll uniquement lorsque le lecteur est en bas. Garder le composeur accessible avec clavier et grande police.
3. Déplacer export/suppression dans des actions secondaires, confirmer la suppression et conserver stop/retry précis.
4. Traiter empty/no-profile/loading/streaming/error/interrupted/confirmation ; traduire les métadonnées et labels d’action.
5. Appliquer insets et navigation adaptés aux tailles réellement supportées, TalkBack et effets réduits. Produire une série de captures Android de la matrice UI-12.

## Critères d'acceptation

- [ ] Composeur et action critique visibles en compact, paysage, IME et grande police.
- [ ] Profil/destination, streaming, stop, retry et confirmation identifiables sans couleur seule.
- [ ] Lecture de l’historique non interrompue par autoscroll ; actions secondaires accessibles.
- [ ] Aucune modification des décisions de politique ou des capacités pour simplifier le rendu.

## Vérification

Tests UI comportementaux pour callbacks/insets/états et contrôles sémantiques ; étendre DesignScreenshotTest pour primitives si modifiées. Les captures app restent nécessaires.

```bash
./gradlew :core-ui:testDebugUnitTest :feature-conversation:testDebugUnitTest
./gradlew assembleCoreDebug assemblePowerUserDebug
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Pas de nouvelle identité graphique globale ni génération de prototype Web en remplacement de Compose. Ne pas annoncer de captures non exécutées.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Fournir chemins/commit des captures et les tailles non testées à JC-11/12.
