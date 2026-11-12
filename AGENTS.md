# Jean-Calcul V2 — règles pour les agents

Cette branche contient uniquement Jean-Calcul V2. `main` garde l'ancienne application.
Ne PAS réintroduire les modules V1. Ne PAS copier directement le runtime Eta.

## Avant le code
1. Lire `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY.md`.
2. Choisir une issue GitHub V2, critères d'acceptation explicites.
3. Charger `.agents/skills/android-e2e/SKILL.md` et
   `.agents/skills/android-visual-review/SKILL.md`, puis lire `e2e/README.md`.
4. Réaliser une modification verticale minimale. Pas d'abstractions spéculatives.

## Définition de terminé
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passe.
- `./gradlew :app:connectedDebugAndroidTest` passe en émulateur.
- Captures d'écran pertinentes et rapport PASS/FAIL/NOT_RUN. Le fait de prendre
  une capture n'est pas une analyse visuelle par un modèle.
- Comparer l'interface aux références Eta/COUI : navigation, saisie, insets,
  petit écran, mode sombre, texte agrandi, retour tactile et animations réduites.
- Présenter PR + APK du commit + preuves E2E, laisser l'utilisateur accepter/refuser.
- PR ciblant `feature/jean-calcul-v2-foundation`, jamais `main`.

## Sécurité
- Aucun secret dans Git, logs, captures ou artefacts.
- Le modèle propose ; Android autorise ou refuse les outils.
- Aucune dépendance obligatoire à root ou LSPosed.
- Ne jamais simuler un outil ou une réponse IA non connecté.
- COUI est épinglé; les adaptations Material3 actuelles sont temporaires.
- Code Eta GPL-3.0 non copié sans décision juridique explicite.

## Boucle locale #54
Lancer `python3 e2e/run.py --run-id <issue> --attempt 1` ; ouvrir réellement
chaque PNG avec la vision native, renseigner `visual-review.json`, puis lancer
`python3 e2e/report.py <dossier>`. Corriger et retester au maximum trois fois.
Une capture seule vaut NOT_RUN pour la revue visuelle. Aucune vision en CI.
