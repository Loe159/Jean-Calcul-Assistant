# JC-11 — Exécuter la qualification automatisée et assembler les preuves

## Contrat de mission

- Issues parentes : [#39](https://github.com/Loe159/Jean-Calcul-Assistant/issues/39), [#46](https://github.com/Loe159/Jean-Calcul-Assistant/issues/46). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-06, JC-07, JC-08, JC-09, JC-10.
- Skill : [$jc-verify](../../../.agents/skills/jc-verify/SKILL.md).
- Exigences : QA-01, QA-02, QA-03, QA-04, QA-05, QA-07, QA-08.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Les suites de modules ne prouvent pas seules que le parcours assemblé garde les mêmes garanties. Les critères physiques et externes doivent rester distincts.

Livrer : Un rapport de qualification par exigence avec commandes, résultats et preuves ; corrections limitées aux défauts de l’intégration des missions.

## Lecture minimale

- [07-quality-and-acceptance.md](../../specs/07-quality-and-acceptance.md)

Sources à inspecter, avec leurs tests de module :

- [.github/workflows/android.yml](../../../.github/workflows/android.yml)
- [docs/agents/verification.md](../../../docs/agents/verification.md)
- [docs/observability/phase_1_mvp_validation.md](../../../docs/observability/phase_1_mvp_validation.md)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Vérifier présence réelle des commits dépendants et relire les critères, pas seulement leur compte rendu.
2. Exécuter les suites/compilations de la matrice, Core et Power User, analyse statique et documentation ; collecter rapports sans secrets.
3. Jouer les scénarios intégrés fake texte/voix/outil/refus/cancel/revocation/process death où automatisables. Vérifier qu’aucun fake ne couvre un critère dit physique.
4. Contrôler exports, migrations et capacités annoncées ; mettre à jour état réel et docs des comportements livrés avec commit.
5. Assembler les cas encore manuels pour JC-12, avec instructions minimales et résultats attendus.

## Critères d'acceptation

- [ ] Builds/tests/analyses requis ont des résultats exacts ou un blocage expliqué.
- [ ] Chaque exigence de mission dépendante est reliée à une preuve, sans transformer NON EXÉCUTÉ en PASS.
- [ ] Aucun critère Samsung/auth réelle n’est validé par la seule CI.
- [ ] Le rapport permet de reproduire un défaut et de connaître la prochaine preuve nécessaire.

## Vérification

Utiliser les commandes complètes de verification.md ; ajouter uniquement un test intégration manquant qui prouve une frontière ou régression réelle.

```bash
python3 scripts/check_agent_docs.py
./gradlew --no-daemon build assembleCoreDebug assemblePowerUserDebug test
./gradlew --no-daemon ktlintCheck detekt lint lintCoreDebug lintPowerUserDebug
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Pas de release, fermeture automatique de #46, ni refonte de features futures pour faire paraître le jalon complet.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. JC-12 reçoit build identifié, rapports et liste exhaustive des cas physiques/externe restants.
