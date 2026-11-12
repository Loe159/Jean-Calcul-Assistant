---
name: issue-to-preview
description: Transformer une issue V2 en PR avec APK, preuves des tests techniques et revue visuelle par l'agent implémenteur.
---

1. Lire `AGENTS.md`, les docs V2 et l'issue avec critères d'acceptation.
2. Implémenter une modification minimale sur une branche V2 dédiée.
3. Charger et **exécuter** `android-e2e` et `android-visual-review`. Aucun score UX issu d'un workflow GitHub ne remplace l'inspection par l'agent qui développe.
4. Limiter les corrections à **3 tentatives** documentées. Indiquer FAIL/NOT_RUN lorsque la vérification n'est pas possible.
5. Construire l'APK du dernier commit et remettre un lien APK réellement disponible, le SHA, les captures locales non privées et `report.md`.
6. Ouvrir la PR vers `feature/jean-calcul-v2-foundation`. Pas de fusion/acceptation automatique : décision de l'utilisateur.
