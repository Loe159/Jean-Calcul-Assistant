# Plan d’implémentation

Cette documentation complète le backlog GitHub. Elle décrit la cible, les contraintes et le contenu détaillé des phases. Les issues restent la source de vérité pour l’avancement et les critères d’acceptation exécutables.

## Lecture rapide pour un agent

Pour une issue de phase 0 ou 1 :

1. lire `AGENTS.md` ;
2. lire l’issue ;
3. lire le document de phase ;
4. lire uniquement les documents transverses référencés par cette phase.

## Documents transverses

- [`00-product-and-principles.md`](00-product-and-principles.md) — vision, objectifs, périmètre et principes.
- [`01-system-architecture.md`](01-system-architecture.md) — composants Android, Gateway, agents et flux d’actions.
- [`02-technology-and-repository.md`](02-technology-and-repository.md) — technologies, modules et conventions de dépôt.
- [`03-security-permissions-and-data.md`](03-security-permissions-and-data.md) — sécurité, permissions, confidentialité et données.
- [`04-roadmap-and-dependencies.md`](04-roadmap-and-dependencies.md) — ordre global, dépendances et critères de passage.

## Phases

- [`phases/phase-0-android-validation.md`](phases/phase-0-android-validation.md) — validation technique Android/Samsung. Epic GitHub : #7.
- [`phases/phase-1-assistant-mvp.md`](phases/phase-1-assistant-mvp.md) — assistant vocal minimal et configurable. Epic GitHub : #17.
- [`phases/phase-2-notifications-tasks-calendar.md`](phases/phase-2-notifications-tasks-calendar.md) — inbox de notifications, tâches et calendrier.
- [`phases/phase-3-gateway-hermes.md`](phases/phase-3-gateway-hermes.md) — Gateway auto-hébergé et intégration Hermes.
- [`phases/phase-4-skills-lifeos.md`](phases/phase-4-skills-lifeos.md) — skills, mémoire, TELOS et compatibilité LifeOS.
- [`phases/phase-5-dashboard-email.md`](phases/phase-5-dashboard-email.md) — dashboard personnel, mails et automatisations.
- [`phases/phase-6-hardening-delivery.md`](phases/phase-6-hardening-delivery.md) — sécurité, performance, tests et distribution.

## Relation entre documentation et issues

Une phase décrit :

- son objectif utilisateur ;
- son architecture ;
- ses technologies ;
- ses difficultés et contraintes ;
- les travaux attendus ;
- les critères de sortie.

Une issue décrit :

- une unité de travail limitée ;
- ses dépendances exactes ;
- ses critères d’acceptation ;
- sa priorité.

Lorsqu’une nouvelle contrainte importante apparaît pendant l’implémentation, mettre à jour le document de phase concerné et l’issue active.
