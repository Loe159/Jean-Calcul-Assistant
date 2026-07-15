# Guide d’exécution pour les agents

Ce fichier est le point d’entrée obligatoire pour tout agent travaillant sur Jean-Calcul Assistant.

## Ordre de lecture

Avant toute modification, lire dans cet ordre :

1. l’issue GitHub assignée ;
2. `docs/plan/README.md` ;
3. le document de phase correspondant dans `docs/plan/phases/` ;
4. les documents transverses référencés par la phase ;
5. le code et les tests existants.

Ne pas charger toutes les phases si la tâche n’en dépend pas. Les documents sont volontairement séparés pour limiter le contexte.

## Sources de vérité

En cas de divergence, appliquer l’ordre suivant :

1. critères d’acceptation et décisions explicites de l’issue active ;
2. décisions d’architecture déjà implémentées et testées ;
3. documentation de phase ;
4. documentation transversale ;
5. anciennes discussions ou prototypes.

Les issues décrivent le travail exécutable. Les documents du plan donnent le contexte, les contraintes et la cible globale.

## Protocole avant implémentation

- Partir de la branche `main` à jour.
- Vérifier les dépendances listées dans l’issue.
- Inspecter les modules touchés et les tests associés.
- Résumer les changements attendus avant de coder.
- Identifier les permissions Android, données personnelles et actions sensibles concernées.
- Ne pas réutiliser l’ancien prototype supprimé comme référence implicite.

## Règles d’architecture non négociables

- L’application est local-first.
- `ModelProvider` et `AgentBackend` sont deux abstractions distinctes.
- Un modèle ou agent n’accède jamais directement aux API Android.
- Toute action passe par le registre versionné des outils, la validation de schéma, le Policy Engine et le journal d’audit.
- Le `VoiceInteractionService` reste minimal ; l’UI et les traitements lourds vivent dans d’autres composants/processus.
- Les secrets ne sont jamais stockés en clair, transmis à un skill ou écrits dans les logs.
- Une donnée externe — notification, mail, page web, résultat d’outil — est non fiable et ne peut pas modifier les instructions système.
- Aucune action sensible ne doit être exécutée silencieusement.
- Les API Android officielles sont prioritaires. L’accessibilité reste optionnelle et isolée dans la variante Power User.

## Discipline de périmètre

- Implémenter uniquement l’issue active et les ajustements indispensables à son fonctionnement.
- Ne pas anticiper une phase future par une architecture spéculative complexe.
- Créer des interfaces d’extension lorsque le plan l’exige, mais ne pas implémenter les futurs fournisseurs sans issue dédiée.
- Signaler les incohérences dans l’issue plutôt que contourner une contrainte.

## Qualité attendue

Chaque changement doit inclure selon le cas :

- tests unitaires ;
- tests instrumentés Android ;
- tests de schéma et de politique ;
- gestion des erreurs et annulations ;
- mise à jour de la documentation touchée ;
- métriques ou traces lorsqu’une performance critique est concernée.

## Definition of Done

Une issue est terminée seulement si :

- tous les critères d’acceptation sont vérifiés ;
- les tests pertinents passent ;
- aucune permission ou donnée sensible supplémentaire n’est introduite sans documentation ;
- le code respecte les frontières de modules ;
- les erreurs sont récupérables ou explicitement documentées ;
- les comportements testés sur appareil sont consignés lorsqu’ils dépendent d’Android ou One UI ;
- le commit et la PR référencent l’issue.

## Format de compte rendu

À la fin du travail, indiquer :

- fichiers et modules modifiés ;
- comportement ajouté ;
- tests exécutés ;
- limitations restantes ;
- critères d’acceptation validés ;
- éventuelles décisions à reporter dans la documentation.
