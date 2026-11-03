# 0018 — Contrat documentaire pour délégation aux agents

Date : 2026-11-03. Statut : décision documentaire pour la remise en état du produit.

## Problème

Le plan décrit un socle avancé alors que des intégrations UI/runtime restent incomplètes. Les
ADR 0016 et 0017 décrivent deux chemins d'authentification différents ; la présence du code ne
qualifie pas le support externe. Un agent disposant d'un contexte limité risque de confondre
interface, implémentation et preuve physique, puis de continuer une phase future trop tôt.

## Décision

- AGENTS.md devient le routeur court vers docs/agents et les instructions des sous-arbres.
- docs/specs porte les comportements cibles identifiés ; docs/agents/current-state date les faits.
- Douze missions bornées complètent les issues #40 à #46 et l'epic #39 sans les clôturer.
- .agents/skills fournit des procédures de développement portables, distinctes des skills produit.
- Un manifest et un vérificateur contrôlent liens, sources, exigences et graphe de dépendances.
- Les résultats distinguent compilation, fake, instrumentation, service réel et essai Samsung.

Les décisions de cadrage choisies pour faciliter l'implémentation sont explicites dans les specs :
sélection unique avec normalisation héritée, un seul tour actif dans le runtime MVP, retry du dernier
tour uniquement, nouvelle conversation lors d'un changement de destination et confirmation R2 en STRICT.
Elles peuvent évoluer par décision utilisateur/issue et tests correspondants, pas par un contournement UI.

## Conséquences et limites

La documentation ne garantit pas un accès OAuth ChatGPT ni une reprise distante durable. La future
preuve de support doit actualiser AUTH-01 et les ADR sans effacer les observations historiques.
Le contrôle documentaire ne remplace pas build/tests/revue de produit. Les phases 2 à 6 restent
cadrées et différées ; leurs détails fournisseurs ne sont pas inventés pour remplir des spécifications.

Références : [portail](../agents/README.md), [specs](../specs/README.md),
[missions](../agents/tasks/README.md), [état réel](../agents/current-state.md).
