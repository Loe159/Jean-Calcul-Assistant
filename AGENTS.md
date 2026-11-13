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
- Captures d'écran pertinentes pour la fonctionnalité modifiée et ses régressions
  plausibles, avec rapport PASS/FAIL/NOT_RUN. Le fait de prendre
  une capture n'est pas une analyse visuelle par un modèle.
- Comparer les états concernés aux références Eta/COUI et vérifier leurs
  variantes pertinentes : navigation, saisie, insets, petit écran, mode sombre,
  texte agrandi, retour tactile et animations réduites. Élargir si un composant
  partagé ou le thème global change ; marquer les autres états NOT_RUN.
- Présenter PR + APK du commit + preuves E2E, laisser l'utilisateur accepter/refuser.
- PR ciblant `feature/jean-calcul-v2-foundation`, jamais `main`.

## Sécurité
- Aucun secret dans Git, logs, captures ou artefacts.
- Le modèle propose ; Android autorise ou refuse les outils.
- Aucune dépendance obligatoire à root ou LSPosed.
- Ne jamais simuler un outil ou une réponse IA non connecté.
- COUI est épinglé; les adaptations Material3 actuelles sont temporaires.
- Code Eta GPL-3.0 non copié sans décision juridique explicite.

## Boucle de test rapide
Pendant l'itération, lancer `python3 scripts/install-preview.py [--serial SERIAL]`
sur un téléphone ADB autorisé. Cela compile et installe l'application distincte
`fr.loevan.jeancalcul.v2.preview`, sans attendre la CI ni toucher à V2 et ses
données. Recueillir le retour utilisateur sur la fonctionnalité en cours. Une
signature différente peut empêcher une mise à jour de Preview : ne jamais
désinstaller automatiquement une application.

## Boucle E2E de livraison #54
Lancer `python3 e2e/run.py --run-id <issue> --attempt 1` ; ouvrir réellement
les PNG du périmètre visuel de l'issue avec la vision native, renseigner
`visual-review.json` (NOT_RUN pour les autres), puis lancer
`python3 e2e/report.py <dossier>`. Corriger et retester au maximum trois fois.
Une capture seule vaut NOT_RUN pour la revue visuelle. Aucune vision en CI.
