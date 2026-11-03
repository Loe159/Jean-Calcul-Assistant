# Prompt prêt à transmettre

Copier le bloc et remplacer l'ID de mission si nécessaire. Les références sont relatives au dépôt.

```text
Travaille dans Jean-Calcul-Assistant sur la mission JC-01.

Lis AGENTS.md, docs/agents/README.md et docs/agents/current-state.md, puis retrouve JC-01
dans docs/agents/tasks/manifest.json. Lis sa fiche, les specs et le SKILL.md liés, ainsi que
les AGENTS.md des sous-arbres touchés. Inspecte les sources/tests avant d'éditer.

Vérifie les dépendances dans la branche réelle. Implémente cette mission uniquement, avec ses
ajustements indispensables. Préserve les données et le travail préexistant. Respecte les
frontières ModelProvider/AgentBackend et le circuit Android de politique/confirmation/audit.

Tu peux résoudre les choix techniques courants dans ce périmètre. Ne remplace pas un service
externe par une simulation annoncée comme réelle et ne démarre pas les phases différées.

Valide selon docs/agents/verification.md et les critères de la fiche. Termine avec
docs/agents/templates/result.md : fichiers, exigences, commandes exactes, résultats, preuves,
limites et prochaine mission éligible. N'annonce pas de test physique ou d'authentification
réelle sans l'avoir exécuté. Ne fusionne pas et ne publie pas de release.
```

Pour une mission ultérieure, remplacer les deux occurrences de JC-01. Si GitHub est accessible,
lire l'issue parent indiquée et ses décisions nouvelles ; sinon le signaler dans le résultat.
Pour une revue, préciser « lecture seule », fournir diff/commit et demander le rôle de relecteur
de [roles.md](../roles.md) avec verdict par critère.
