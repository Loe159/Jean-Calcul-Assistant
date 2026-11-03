# Portail de délégation aux agents IA

Ce dossier permet de confier l'implémentation à un agent disposant d'un contexte limité.
Chaque mission fournit des sources, des décisions et des preuves attendues. L'agent n'a
pas à réinventer le produit ni son architecture.

## Démarrage

1. Lire les [instructions racine](../../AGENTS.md).
2. Lire l'[état réel](current-state.md).
3. Choisir la première mission éligible du [catalogue](tasks/README.md).
4. Charger uniquement sa spécification et son skill.
5. Inspecter les sources, tests et instructions locales cités.

Pour démarrer, donner à l'agent le [prompt de délégation](templates/delegation.md) avec `JC-01`.
Pour une revue, utiliser le [rôle de relecteur](roles.md) et le [modèle de résultat](templates/result.md).

## Carte documentaire

| Besoin | Document | Quand le lire |
| --- | --- | --- |
| Comprendre les écarts | [État réel](current-state.md) | À chaque mission |
| Trouver les modules | [Carte du code](code-map.md) | Avant de choisir les fichiers |
| Travailler sans dériver | [Workflow](workflow.md) | Premier travail dans le dépôt |
| Répartir implémentation/revue | [Rôles](roles.md) | Travail délégué |
| Préparer et vérifier | [Validation](verification.md) | Avant les checks |
| Connaître le comportement cible | [Spécifications](../specs/README.md) | Selon la mission |
| Choisir une unité de travail | [Missions](tasks/README.md) et [manifest](tasks/manifest.json) | Au démarrage |
| Réutiliser une procédure | [Skills](skills.md) | Selon le chantier |
| Relire la stratégie longue | [Plan existant](../plan/README.md) | Si la mission en dépend |

## Statut des informations

- **OBSERVÉ** : lecture statique de sources, au commit indiqué. Pas une validation d'exécution.
- **CIBLE** : exigence à implémenter dans la mission référente.
- **À VALIDER** : fait dépendant d'un appareil, service externe, compte ou mesure absent de cette livraison.
- **DIFFÉRÉ** : cadrage d'une phase future, pas une tâche prête à coder.

Les identifiants `CONV-*`, `POL-*`, `AUTH-*`, `DATA-*`, `VOICE-*`, `UI-*`, `QA-*` désignent
des exigences stables. Le résultat d'une mission les référence. Un nom de classe proposé
dans une spec reste une suggestion, sauf s'il désigne un contrat déjà présent.

Les specs exécutables couvrent la remise en état du MVP et la phase 1B. Les phases 2 à 6 ont
un [cadrage par domaine](../specs/08-future-phases.md), des contraintes et portes de sortie,
sans protocole fournisseur inventé ni module vide à générer. GitHub reste la référence de suivi.
Le manifest décrit dépendances et ordre, sans colonne « terminé » concurrente aux issues.

Cette livraison documentaire ne modifie pas le runtime et ne valide pas l'authentification
réelle. La [matrice de validation](verification.md) précise les preuves à produire.
