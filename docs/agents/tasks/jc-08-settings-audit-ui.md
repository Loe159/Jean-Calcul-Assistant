# JC-08 — Clarifier mise en service, réglages et audit

## Contrat de mission

- Issues parentes : [#39](https://github.com/Loe159/Jean-Calcul-Assistant/issues/39), [#43](https://github.com/Loe159/Jean-Calcul-Assistant/issues/43), [#44](https://github.com/Loe159/Jean-Calcul-Assistant/issues/44). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : JC-01, JC-04, JC-09.
- Skill : [$jc-compose-ui](../../../.agents/skills/jc-compose-ui/SKILL.md).
- Exigences : UI-01, UI-02, UI-03, UI-04, UI-05, UI-10, UI-11, UI-12, PROD-03, PROD-04, PROD-06.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Le premier parcours doit distinguer rôle, microphone et backend ; les profils/connexions et les garanties d’authentification sont difficiles à comprendre ; l’audit doit montrer aussi refus/échecs.

Livrer : Une mise en service progressive et des réglages lisibles avec assistant effectif unique, état auth honnête et audit filtrable/expurgé.

## Lecture minimale

- [06-ux-and-design.md](../../specs/06-ux-and-design.md)
- [03-backends-auth-and-protocol.md](../../specs/03-backends-auth-and-protocol.md)

- [00-product.md](../../specs/00-product.md)

Sources à inspecter, avec leurs tests de module :

- [android/app/src/main/java/fr/loevan/jeancalcul/app/AssistantRoleOnboarding.kt](../../../android/app/src/main/java/fr/loevan/jeancalcul/app/AssistantRoleOnboarding.kt)
- [android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsScreen.kt](../../../android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsScreen.kt)
- [android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsViewModel.kt](../../../android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsViewModel.kt)
- [android/app/src/main/java/fr/loevan/jeancalcul/app/AuditScreen.kt](../../../android/app/src/main/java/fr/loevan/jeancalcul/app/AuditScreen.kt)
- [android/app/src/main/java/fr/loevan/jeancalcul/app/AuditViewModel.kt](../../../android/app/src/main/java/fr/loevan/jeancalcul/app/AuditViewModel.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Séparer statut rôle, micro et backend ; ne pas bloquer le texte derrière la demande de rôle/micro.
2. Regrouper connexions, profils modèles/agents, voix, apparence et confidentialité ; montrer l’activation effective de JC-01 et les capacités réelles.
3. Distinguer saved-secret, remplacement/suppression et résultat daté du probe. Le direct non vérifié est indiqué comme tel selon AUTH-01.
4. Clarifier audit : décision versus exécution, refus/erreur/rejeu, filtres, détail, export et purge confirmée.
5. Vérifier empty/loading/error/long content, thèmes, font-scale, TalkBack et retour natif sur les écrans réels.

## Critères d'acceptation

- [ ] Un nouvel utilisateur identifie l’étape manquante et peut utiliser le texte sans microphone.
- [ ] Un seul profil actif affiché ; endpoint et secret ont des fonctions distinctes.
- [ ] Aucun secret réaffiché et aucune promesse d’accès ChatGPT non prouvée.
- [ ] Audit différencie refus, exécution, erreur et rejeu ; purge/partage ont une portée claire.

## Vérification

Étendre SettingsViewModelTest et tests d’actions/sémantiques UI selon changements ; captures app des réglages et audit. Les snapshots de previews ne remplacent pas les vrais écrans.

```bash
./gradlew :feature-settings:testDebugUnitTest :core-ui:testDebugUnitTest
./gradlew assembleCoreDebug assemblePowerUserDebug
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Ne pas implémenter un Doctor complet, dashboard futur, nouvelle auth ou permission automatique.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. Fournir les affirmations publiques retirées/justifiées et captures à JC-10/11.
