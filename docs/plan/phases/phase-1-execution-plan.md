# Phase 1 — Plan d’exécution et parallélisation

Epic : #17  
Périmètre fonctionnel : #18 à #33  
Fondation technique complémentaire : #36

Ce document complète `phase-1-assistant-mvp.md`. Les issues restent la source de vérité pour le périmètre et les critères d’acceptation.

## État d’entrée

La phase 1 peut commencer :

- l’epic de phase 0 #7 est fermé ;
- les issues #8 à #16 sont fermées ;
- l’invocation Power physique, la session transparente, le STT/TTS, les outils volume et le parcours vocal ont été validés sur le Samsung cible ;
- les budgets initiaux sont documentés dans `docs/observability/phase-0-performance-stability.md` ;
- le comportement limité sur écran verrouillé doit être traité par le Policy Engine de #28, et non contourné.

## Règle de démarrage

Trois travaux peuvent commencer immédiatement et en parallèle :

1. #18 — design system dans `core-ui` ;
2. #21 — contrats modèles/agents dans `core-domain` ;
3. #36 — modules, catalogue de versions et configuration Gradle de la phase 1.

#29 démarre dès que #36 est terminé. Aucun fournisseur réseau ne commence avant stabilisation de #21, #29 et #36 selon ses dépendances.

## Graphe de dépendances

```text
#18 Design system ───────→ #19 Machine d’états ──────┬→ #20 Pipeline vocal
                                                      ├→ #26 Conversations
#21 Contrats ────────────→ #27 Registre outils ──────┤
          │                                           └→ #28 Policy Engine
          │                                                    │
#36 Modules ─→ #29 Secrets ─┬→ #22 OpenAI-compatible          ├→ #30 Configuration
          │                 ├→ #23 Anthropic                    └→ #32 Audit
          │                 └→ #24 OpenRouter                        │
          ├────────────────────→ #25 Ollama                          └→ #31 Outils MVP
          ├────────────────────→ #20 / #26 / #30 / #31
          └────────────────────→ #27

#26 Conversations ─────────────────────────────────────────────→ #32 Audit

#18, #19, #20, #22, #23, #25, #26, #27, #28, #29, #30, #31, #32
  └────────────────────────────────────────────────────────────→ #33 Validation finale
```

#24 reste P1. Il doit être terminé ou explicitement reporté avant la fermeture de #17.

## Lots parallèles recommandés

### Lot A — fondations immédiates

En parallèle : #18, #21 et #36.

Ces travaux ont des zones d’écriture distinctes. Seule #36 modifie `settings.gradle.kts`, le catalogue de versions, les conventions Gradle et la CI.

### Lot B — contrats applicatifs et sécurité

Après les prérequis concernés :

- #19 après #18 ;
- #29 après #36 ;
- #27 après #21 et #36.

#19, #29 et #27 peuvent avancer en parallèle.

### Lot C — voix, conversations et fournisseurs

En parallèle lorsque leurs prérequis sont fermés :

- #20 après #19 et #36 ;
- #26 après #19, #21 et #36 ;
- #22 après #21, #29 et #36 ;
- #23 après #21, #29 et #36 ;
- #25 après #21 et #36.

Les fournisseurs utilisent des sous-packages séparés de `core-network`. Ils ne modifient pas les contrats de `core-domain` ; toute lacune de contrat retourne dans #21 avant poursuite.

#24 peut avancer après #22 et #29 sans bloquer le chemin critique P0.

### Lot D — autorisation et interfaces de gestion

- #28 commence après #19 et #27.
- #30 commence après #18, #21, #28, #29 et #36.
- #32 commence après #26, #27 et #28 afin de réutiliser la base Room initialisée par #26.
- #31 commence après #27, #28, #32 et #36 afin que chaque nouvel outil soit audité dès son intégration.

#30 et #32 peuvent avancer en parallèle après #28. #31 suit #32.

### Lot E — validation

#33 est le dernier lot. Il ne commence qu’après fermeture de toutes ses dépendances. Les tests unitaires, tests de schéma, tests de politique et tests réseau simulés doivent toutefois être ajoutés dans chaque issue, sans attendre #33.

## Chemin critique

Le chemin critique estimé est :

```text
#18 → #19 ─┐
            ├→ #28 → #32 → #31 → #33
#21 → #27 ─┘
```

Les fournisseurs, le pipeline vocal, les conversations et les écrans de configuration doivent être exécutés en parallèle de ce chemin dès que leurs contrats sont disponibles.

## Ownership des modules

| Issue | Zone principale |
| --- | --- |
| #36 | structure Gradle, catalogue, CI et création des modules |
| #18 | `core-ui` |
| #19 | `core-domain` pour la machine d’états, intégration UI minimale dans la session |
| #20 | `feature-voice`, `assistant-session` |
| #21 | contrats et fakes dans `core-domain` |
| #22 | adaptateur OpenAI-compatible dans `core-network` |
| #23 | adaptateur Anthropic dans `core-network` |
| #24 | spécialisation OpenRouter dans `core-network` |
| #25 | adaptateur Ollama dans `core-network` |
| #26 | `feature-conversation`, stockage conversation dans `core-data` |
| #27 | registre et exécution dans `tool-bridge`, contrats d’outil dans `core-domain` |
| #28 | politique dans `core-domain`, intégration confirmation dans l’UI |
| #29 | `core-security` |
| #30 | `feature-settings`, navigation dans `app` |
| #31 | outils dans `tool-bridge`, tâche locale dans `feature-tasks` |
| #32 | audit dans `core-observability`, persistance dans `core-data`, consultation dans `app` |
| #33 | tests d’intégration, benchmark et rapport de sortie |

## Règles pour plusieurs agents

- Une issue par branche ou worktree.
- Rebase sur `main` après fermeture de chaque dépendance directe.
- Seule #36 modifie la structure globale Gradle pendant le lot A.
- Après #21, les issues consommatrices ne modifient pas les contrats partagés sans rouvrir ou amender explicitement #21.
- Les providers utilisent des fichiers et sous-packages distincts.
- #26 possède la création initiale de la base Room ; #32 ajoute l’audit via une migration testée.
- Une issue n’est fusionnée que lorsque ses tests propres passent ; #33 n’est pas un rattrapage des tests manquants.

## Ordre conseillé pour un développeur unique

```text
#36 → #21 → #18 → #29 → #19 → #27 → #22 → #23 → #25
→ #20 → #26 → #28 → #32 → #30 → #31 → #24 → #33
```

Cet ordre privilégie le chemin critique. Avec plusieurs agents, utiliser les lots parallèles ci-dessus.