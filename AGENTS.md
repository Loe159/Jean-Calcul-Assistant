# Jean-Calcul V2 — règles obligatoires pour tout agent développeur

Cette branche contient Jean-Calcul V2 uniquement. `main` conserve V1.
Ne réintroduire aucun module V1. Ne pas copier le runtime GPL d'Eta.

## Avant toute modification
1. Lire `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY.md` et l'issue GitHub.
2. Charger `.agents/skills/issue-to-preview/SKILL.md` puis `.agents/skills/android-e2e/SKILL.md` et `.agents/skills/android-visual-review/SKILL.md`.
3. Définir les scénarios fonctionnels/UX/stabilité affectés. Éditer une tranche verticale minimale.

## Boucle de validation obligatoire — exécutée PAR l'agent qui code
1. Compiler, tester et démarrer un émulateur Android isolé, **sans données personnelles**.
2. Exécuter `bash e2e/agentic_loop.sh run 1` (voir skill pour les dépendances).
3. **Ouvrir réellement les PNG avec une capacité de vision/image de l'agent**. Ne pas déduire une qualité d'interface de la seule présence des fichiers, des dimensions, du lint, d'un hash ou d'un score pixel.
4. Examiner chaque état : vide, réglages, message, reset, sombre, petit écran, police agrandie et clavier. Vérifier les insets, chevauchements, contraste, hiérarchie COUI et navigation. Vérifier que les images montrent l'application, **pas un dialogue Android d'ANR/crash**.
5. Enregistrer chaque verdict argumenté via `python3 e2e/record_verdict.py`; générer `report.json` et `report.md` via `bash e2e/agentic_loop.sh report 1`.
6. Si FAIL ou NOT_RUN : diagnostiquer, corriger soi-même, puis relancer l'itération 2, éventuellement 3. **Trois exécutions maximum** pour une issue; si non résolu, signaler le blocage, ne pas certifier la qualité.
7. Recompiler l'APK du commit final. Livrer PR, APK, captures **expurgées** et rapport associé exactement au commit. L'utilisateur accepte/refuse.

Si la capacité de voir les captures, l'émulateur ou les scénarios est indisponible, marquer **NOT_RUN** ; ne jamais affirmer « revue visuelle PASS ». Un test instrumenté PASS n'est pas une preuve UX. Aucune approbation ou fusion automatique.

## Responsabilité de GitHub Actions
CI = compilation, tests JVM/lint, tests instrumentés techniques, artefact APK et rapports techniques. **Pas de modèle vision, pas d'appel API LLM, pas d'agent correcteur ni de notation esthétique dans Actions.** L'agent développeur applique le skill avant soumission de PR.

## Git / périmètre / sécurité
- PR vers `feature/jean-calcul-v2-foundation`, jamais vers `main` pour les issues V2.
- Aucun secret, donnée privée, dump système complet ou capture d'un téléphone personnel dans Git/PR.
- Les contrôles Android sont soumis aux permissions utilisateur, sans root/LSPosed obligatoire.
- Aucune réponse ou outil IA simulé comme fonctionnel.
- COUI est épinglé; les contrôles Material3 restent des adaptateurs transitoires.
