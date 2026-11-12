# Qualité V2 — agent développeur responsable de la revue visuelle

Le cycle obligatoire : issue → implémentation → build/tests → émulateur **local à l'agent**
→ captures → **inspection visuelle par cet agent** → verdicts motivés → correction
→ re-test (3 tentatives max) → APK + PR → décision de l'utilisateur.

## GitHub Actions = CI technique uniquement
GitHub Actions peut compiler et publier l'APK, exécuter JVM/lint et tests
instrumentés sur Android. **Il ne réalise pas la revue visuelle, n'invoque
aucun modèle/agent vision et ne corrige pas les sources.** Les artefacts de CI
ne constituent pas une attestation de qualité visuelle.

## Skills et scripts obligatoires
- `.agents/skills/issue-to-preview/SKILL.md` : cycle issue → PR et livraison.
- `.agents/skills/android-e2e/SKILL.md` : instrumentation Compose/UI Automator, ADB, captures.
- `.agents/skills/android-visual-review/SKILL.md` : revue d'images par l'agent implémenteur.
- `e2e/agentic_loop.sh run N` : une tentative reproductible, N=1..3.
- `e2e/record_verdict.py` : constats réellement observés, PASS/FAIL/NOT_RUN.
- `e2e/agentic_loop.sh report N` : agrégation sans appel à une API vision.

## Critères non négociables
- Une image PNG existante, des dimensions valides ou une différence pixel ne
  suffisent jamais pour conclure PASS visuel.
- Captures avec dialogs Android d'ANR/crash = FAIL (non recevables).
- Les données sont **synthétiques** sur un émulateur dédié. Pas d'uploads de
  captures privées ou de secrets, pas de dumps système complets.
- Absence d'émulateur, incapacité à voir les images, références manquantes :
  NOT_RUN (ou FAIL sur un défaut avéré). Ne pas annoncer une revue faite.
- Chaque rapport précise issue, commit, API Android, captures, statuts et
  observations; l'utilisateur valide/refuse l'APK.
