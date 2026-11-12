# Jean-Calcul V2 — règles pour les agents

Cette branche contient uniquement Jean-Calcul V2. `main` garde l'ancienne application.
Ne PAS réintroduire les modules V1. Ne PAS copier directement le runtime Eta.

## Avant le code
1. Lire `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY.md`.
2. Choisir une issue GitHub V2, critères d'acceptation explicites.
3. Consulter les skills du dossier `.agents/skills/`.
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
