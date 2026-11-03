# JC-01 — Rendre la sélection du profil unique et vérifiable

## Contrat de mission

- Issues parentes : [#40](https://github.com/Loe159/Jean-Calcul-Assistant/issues/40), [#43](https://github.com/Loe159/Jean-Calcul-Assistant/issues/43). Leurs critères plus larges restent ouverts si non couverts ici.
- Dépendances à vérifier dans la branche : aucune mission JC préalable.
- Skill : [$jc-runtime](../../../.agents/skills/jc-runtime/SKILL.md).
- Exigences : CONV-01, CONV-02, CONV-04, DATA-05, AUTH-06.
- Statut de cette fiche : **CIBLE**, aucune implémentation/prévalidation déduite de sa présence.

## Problème et résultat

Deux sélections actives peuvent coexister. Activer un modèle ne désactive pas un agent, puis la conversation choisit implicitement l’agent. Le choix affiché ne suffit donc pas à connaître le destinataire réel.

Livrer : Un état effectif Aucun/Modèle/Agent, des activations exclusives et une normalisation compatible des anciennes préférences. Aucun appel distant dans cette mission.

## Lecture minimale

- [01-conversation-runtime.md](../../specs/01-conversation-runtime.md)
- [04-data-and-privacy.md](../../specs/04-data-and-privacy.md)

- [03-backends-auth-and-protocol.md](../../specs/03-backends-auth-and-protocol.md)

Sources à inspecter, avec leurs tests de module :

- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/AssistantSettings.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/AssistantSettings.kt)
- [android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ProviderSelection.kt](../../../android/core-domain/src/main/kotlin/fr/loevan/jeancalcul/domain/ProviderSelection.kt)
- [android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsViewModel.kt](../../../android/feature-settings/src/main/kotlin/fr/loevan/jeancalcul/feature/settings/SettingsViewModel.kt)
- [android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt](../../../android/core-network/src/main/kotlin/fr/loevan/jeancalcul/network/codex/CodexCompanionAgentBackend.kt)

Lire aussi les AGENTS.md applicables et [l'état réel](../current-state.md).

## Implémentation guidée

1. Écrire des tests de transformations de réglages : aucun profil, modèle seul, agent seul, deux valides, un invalide, connexion supprimée/désactivée et mauvais type de connexion.
2. Centraliser la résolution et la validation dans une logique testable sans Context. Conserver les deux champs sérialisés pour ce jalon ; appliquer la règle de priorité héritée de CONV-01.
3. Mettre à jour activateModel/activateAgent et les nettoyages de sélection pour rendre la transformation atomique. Préserver les préférences voix/apparence et les profils non concernés.
4. Exposer le profil effectif aux futurs appelants ; ne pas déplacer les factories réseau dans core-domain. Vérifier que le chemin modèle rejette AGENT_BACKEND même avec des données héritées.

## Critères d'acceptation

- [ ] Après activation du modèle M, seul M est effectif ; activer A sélectionne seulement A.
- [ ] Ancien JSON avec les deux actifs : agent valide conservé ; agent invalide et modèle valide : modèle conservé.
- [ ] Profil ou connexion disabled/supprimé : indisponibilité explicite, aucun fallback caché.
- [ ] Les tests prouvent absence de perte des réglages non concernés et séparation des types.

## Vérification

Étendre AssistantSettingsTest, ProviderSelectionTest et SettingsViewModelTest. Ajouter un test DataStore instrumenté si la sérialisation change réellement.

```bash
./gradlew :core-domain:test :feature-settings:testDebugUnitTest
```

Ces commandes sont à exécuter, pas des résultats acquis. Ajouter les analyses de module et la
[validation d'intégration](../verification.md) selon le diff. L'absence d'environnement est
consignée comme NON EXÉCUTÉ. L'instrumentation ne se lance que sur appareil autorisé.

## Hors périmètre

Ne pas ajouter une troisième préférence active sans migration, un nouveau fournisseur, ni raccorder le streaming UI dans cette mission.

## Sortie et handoff

Remplir [le résultat](../templates/result.md) avec le mapping exigence→preuve, les fichiers,
commandes exactes, limites et critères non exécutés. JC-02 et JC-04 peuvent ensuite démarrer ; fournir la fonction de résolution et sa sémantique héritée.
