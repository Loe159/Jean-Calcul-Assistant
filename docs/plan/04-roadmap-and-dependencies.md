# Roadmap et dépendances

## Ordre global

```text
Phase 0 — Validation Android/Samsung [terminée]
  ↓
Phase 1 — Assistant vocal MVP [implémentée, validations physiques résiduelles]
  ↓
Phase 1B — Boucle Codex utilisable [active, bloquante]
  ├── Phase 2 — Notifications, tâches, calendrier
  └── Phase 3 — Gateway et Hermes
        ↓
      Phase 4 — Skills, mémoire et LifeOS
        ↓
      Phase 5 — Dashboard et mails
        ↓
      Phase 6 — Finalisation et livraison
```

La phase 6 reste également transversale : sécurité, tests et observabilité commencent bien avant sa clôture.

## Phase 1 → Phase 1B

Le socle nécessaire existe déjà :

- contrats `ModelProvider` et `AgentBackend` ;
- conversations persistées et streaming ;
- registre d’outils et Policy Engine ;
- secrets, profils et audit ;
- pipeline voix/texte.

La phase 1B corrige le dernier écart entre ce socle et un parcours réellement utilisable : l’interface doit parler à un backend Codex réel, appairé de manière privée, et les propositions d’outils doivent revenir jusqu’à Android pour décision et exécution.

Epic : #39. Issues : #40 à #46.

## Phase 1B → Phases 2 et 3

La phase 1B est bloquante pour les nouveaux parcours fonctionnels. Avant de poursuivre :

- texte et voix doivent partager le même orchestrateur réel ;
- l’authentification ChatGPT reste confinée au compagnon Codex ;
- l’appairage Android-compagnon doit être révocable et protégé ;
- au moins un outil de lecture et un outil à effet doivent fonctionner de bout en bout ;
- refus, annulation, perte réseau et session expirée doivent être récupérables ;
- la validation Samsung de #46 doit être versionnée.

## Dépendances suivantes

### Phase 2

Réutilise le registre d’outils, le Policy Engine, l’audit, le stockage local et l’UI de confirmation. Aucun second chemin d’action ne doit être créé.

### Phase 3

Réutilise `AgentBackend`, le streaming normalisé, les approbations, les profils, les secrets et la reprise de conversation. Hermes reste distinct du compagnon Codex de phase 1B.

### Phases 4 à 6

Les skills et la mémoire dépendent d’outils mobiles et de permissions stables. Le dashboard et les mails s’appuient ensuite sur ces fondations. La phase 6 consolide sécurité, performances, distribution et récupération.

## Priorités

- P0 : bloque un parcours principal ou une garantie de sécurité.
- P1 : nécessaire à la qualité d’usage ou au jalon courant.
- P2 : amélioration pouvant être reportée.

Une phase ne doit pas être déclarée terminée tant que ses P0 obligatoires sont ouverts, sauf report explicite documenté dans l’epic.
