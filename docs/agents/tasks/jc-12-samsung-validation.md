# JC-12 — Valider réellement le parcours sur le Samsung de référence

## Contrat de mission

- Issues parentes : [#46](https://github.com/Loe159/Jean-Calcul-Assistant/issues/46), [#17](https://github.com/Loe159/Jean-Calcul-Assistant/issues/17). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-11.
- Skill : [$jc-verify](../../../.agents/skills/jc-verify/SKILL.md).
- Exigences : QA-01, QA-05, QA-06, QA-08, UI-12, AUTH-01.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Les contraintes Power/One UI/audio/biométrie et l’accès compte ne peuvent pas être qualifiés à partir des mocks ni des rapports d’un ancien commit.

Livrer : Un rapport physique versionné et expurgé, avec vingt tours/vingt outils et résultats cas par cas, ou liste honnête des interventions/appareils manquants.

## Lecture minimale

- [05-voice-and-android-lifecycle.md](../../specs/05-voice-and-android-lifecycle.md)
- [06-ux-and-design.md](../../specs/06-ux-and-design.md)
- [07-quality-and-acceptance.md](../../specs/07-quality-and-acceptance.md)

- [03-backends-auth-and-protocol.md](../../specs/03-backends-auth-and-protocol.md)

Sources à inspecter, avec leurs tests de module :

- [docs/testing/device-matrix.md](../../../docs/testing/device-matrix.md)
- [docs/testing/samsung-power-invocation.md](../../../docs/testing/samsung-power-invocation.md)
- [docs/testing/phase-1-voice-pipeline.md](../../../docs/testing/phase-1-voice-pipeline.md)
- [docs/agents/templates/device-report.md](../../../docs/agents/templates/device-report.md)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Identifier appareil réel, build/commit, variante, backend et autorisation de l’opérateur. Sauvegarder les réglages qui seront modifiés et éviter les données privées.
2. Suivre verification.md : auth, texte, batterie, lampe confirmée/refusée, voix et continuité, réseau, permissions, verrouillage, rotation/relance.
3. Exécuter vingt tours et vingt appels outils en journalisant chacun. Faire un vrai appui Power et appel entrant ; distinguer gestes physiques de KEYCODE_ASSIST.
4. Capturer thèmes/IME/polices/états et vérifier TalkBack. Mesurer avec les jalons et conditions, pas des chiffres estimés.
5. Restaurer les réglages, remplir device-report et lier les preuves expurgées. Ne clôturer les critères que sur preuve ; rester NON EXÉCUTÉ si appareil/compte/geste manque.

## Critères d'acceptation

- [ ] Chaque essai indique observé/attendu et PASS/FAIL/NON EXÉCUTÉ, avec commit.
- [ ] Vingt tours et vingt outils comptés sans double exécution, crash ou corruption ; refus attendus séparés des succès d’exécution.
- [ ] Power/voix/appel entrant/confirmation réelle sont démontrés sur l’appareil déclaré.
- [ ] Aucune trace/capture/export ne contient secret ou demande personnelle ; réglages restaurés.

## Vérification

Procédure réelle de verification.md et suites instrumentées adaptées. Le modèle device-report est à remplir, pas une preuve préexistante.

```bash
adb devices -l
./gradlew :assistant-session:connectedDebugAndroidTest :core-ui:connectedDebugAndroidTest
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Ne pas simuler une biométrie ou un compte, inventer des mesures, publier une release ni installer sur un autre appareil non autorisé.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Décision humaine de clôture de #46/#17 ou missions de correction précises ; aucune release implicite.
