# Plan d’implémentation

Cette documentation complète le backlog GitHub. Les issues restent la source de vérité pour l’avancement et les critères d’acceptation ; les documents du plan décrivent la cible, les contraintes et les dépendances.

## Lecture rapide pour un agent

1. lire `AGENTS.md` ;
2. lire l’issue active ;
3. lire le document de phase correspondant ;
4. charger uniquement les documents transverses référencés par cette phase ;
5. vérifier les décisions d’architecture déjà implémentées avant de modifier les contrats.

Pour les travaux historiques de phase 1, `phases/phase-1-execution-plan.md` reste la référence du graphe de dépendances. Pour le chantier courant, utiliser `phases/phase-1b-usable-codex-loop.md`.

## Documents transverses

- [`00-product-and-principles.md`](00-product-and-principles.md) — vision, objectifs et principes.
- [`01-system-architecture.md`](01-system-architecture.md) — composants Android, Gateway, agents et flux d’actions.
- [`02-technology-and-repository.md`](02-technology-and-repository.md) — technologies, modules et conventions.
- [`03-security-permissions-and-data.md`](03-security-permissions-and-data.md) — sécurité, permissions, confidentialité et données.
- [`04-roadmap-and-dependencies.md`](04-roadmap-and-dependencies.md) — ordre global et critères de passage.

## Phases

- [`phases/phase-0-android-validation.md`](phases/phase-0-android-validation.md) — validation Android/Samsung, terminée. Epic #7.
- [`phases/phase-1-assistant-mvp.md`](phases/phase-1-assistant-mvp.md) — socle MVP, implémenté ; validations physiques résiduelles dans #17.
- [`phases/phase-1-execution-plan.md`](phases/phase-1-execution-plan.md) — plan d’exécution historique de la phase 1.
- [`phases/phase-1b-usable-codex-loop.md`](phases/phase-1b-usable-codex-loop.md) — chantier actif pour une boucle Codex réellement utilisable. Epic #39.
- [`phases/phase-2-notifications-tasks-calendar.md`](phases/phase-2-notifications-tasks-calendar.md) — notifications, tâches et calendrier.
- [`phases/phase-3-gateway-hermes.md`](phases/phase-3-gateway-hermes.md) — Gateway auto-hébergé et intégration Hermes.
- [`phases/phase-4-skills-lifeos.md`](phases/phase-4-skills-lifeos.md) — skills, mémoire, TELOS et LifeOS.
- [`phases/phase-5-dashboard-email.md`](phases/phase-5-dashboard-email.md) — dashboard, mails et automatisations.
- [`phases/phase-6-hardening-delivery.md`](phases/phase-6-hardening-delivery.md) — sécurité, performance, tests et distribution.

## Règle de mise à jour

Lorsqu’une contrainte change, mettre à jour l’issue active et le document de phase concerné. Ne dupliquer ni le statut des issues dans plusieurs fichiers, ni des mini-roadmaps parallèles : les issues donnent l’état courant, le plan donne le contexte durable.
