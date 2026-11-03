# Workflow d'une mission déléguée

## Préparer

L'agent reçoit un ID JC, l'issue associée et le périmètre autorisé. Vérifier que les dépendances
sont livrées dans sa branche ; leur présence dans le manifest ne suffit pas. Sans accès GitHub,
utiliser la fiche versionnée et signaler que le statut distant n'a pas été vérifié.

1. Inspecter branche, diff préexistant et commit de base.
2. Lire fiche, spec, skill, instructions locales, sources et tests cités.
3. Associer chaque critère à une observation ou un test. Reproduire le défaut si possible.
4. Annoncer le résultat et les vérifications ; choisir le changement minimal satisfaisant le contrat.

## Implémenter

- Une branche, une mission ; préserver les changements externes.
- Réutiliser DTO, erreurs, Flow, repositories et tokens.
- Politique dans le domaine, API Android dans les adaptateurs, présentation dans le host/ViewModel.
- Modifier un contrat avec ses appelants/tests dans le même changement.
- Tester concurrence par événements contrôlés, pas par sleeps.
- Tester migration et ancien contenu avant les captures UI.
- Si l'environnement manque, compléter le vérifiable et noter précisément le check bloqué.

## Résoudre les imprévus

| Situation | Décision |
| --- | --- |
| Classe suggérée absente, contrat équivalent présent | Adapter au code et expliquer le mapping |
| Spec contredit un historique | Suivre l'autorité racine et consigner la divergence |
| Auth externe non documentée officiellement | Ne pas inventer endpoint/scope ; diagnostiquer et marquer non vérifié |
| Appareil absent | Livrer procédure et « non exécuté », pas un succès simulé |
| Migration/permission imprévue indispensable | Limiter l'extension et documenter impact/preuve |
| Fusion, release ou compte personnel | Appliquer l'autorisation de session ; sinon préparer le résultat à approuver |

## Vérifier et transmettre

Exécuter la [matrice](verification.md) adaptée à la mission. Relancer après correction le check
en échec puis les checks requis. Pour l'UI, capturer les états/tailles en une série, corriger en lot
et confirmer. Ne pas ouvrir une optimisation hors mission sans mesure.

Utiliser [le résultat](templates/result.md). « Implémenté avec preuve physique restante » ne
permet pas de fermer les critères physiques de l'issue parente. Joindre diff, commandes et preuves.

Le handoff précise commit/branche, état du diff, décisions, tests déjà exécutés, échecs reproductibles,
prochaine action et fichiers utiles. Aucun secret ni transcription personnelle. Le nouvel agent
vérifie les faits sans recommencer tout l'audit et ne considère pas un ancien check comme valide
pour un nouveau diff.
