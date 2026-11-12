# Agent-local E2E — issue #54

**Responsabilité :** l'agent qui implémente ouvre les captures, les examine
visuellement, inscrit ses constats et corrige/reteste. GitHub Actions se
limite à construire l'APK et aux tests techniques ; aucune clé vision,
aucun jugement UX et aucune correction automatique dans GitHub Actions.

## Prérequis

Java 25, SDK Android 37.0, émulateur AOSP API 34 dédié (vérifié par
`adb get-serialno` et `ro.kernel.qemu`), `adb`, Python 3 et Pillow.

```bash
python3 -m pip install -r e2e/requirements.txt
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 e2e/test_visual_diff.py
bash e2e/agentic_loop.sh run 1
```

Ce dernier script produit `e2e-artifacts/attempt-1/` avec des captures,
`instrumentation.txt`, `instrumentation.log` et, en cas d'échec, `android-errors.log`/captures de diagnostic. Le code de test
instrumenté prend les captures 01–04; ADB prend 05–08 (sombre, petit
écran, police, clavier). Elles sont **non vérifiées** à ce stade.

## Inspection obligatoire par l'agent développeur

Lire `.agents/skills/android-visual-review/SKILL.md`. L'agent utilise
son outil natif d'ouverture d'images pour **voir chacun des PNG**, contrôle
les défauts réels (dont les ANR Android), puis enregistre un verdict
motivé pour les huit critères fonctionnels et UX :

```bash
python3 e2e/record_verdict.py --list
python3 e2e/record_verdict.py \
  --out e2e-artifacts/attempt-1 \
  --criterion ux.keyboard \
  --status FAIL \
  --reason "Le clavier couvre le champ de saisie sur 08-keyboard.png."
bash e2e/agentic_loop.sh report 1
```

Exécuter `record_verdict.py` pour **chaque** critère; stabilité
est calculée par les assertions instrumentées. Un PASS visuel exige
les captures demandées, un constat argumenté et, pour les parcours
fonctionnels, un résultat PASS des tests instrumentés. Une capture
présente n'est **jamais** un PASS automatique. Sans inspection,
le critère demeure NOT_RUN.

Le rapport `report.json`/ `report.md` indique PASS/FAIL/NOT_RUN,
les preuves et les constats. `report` retourne un code non nul tant
qu'il reste un échec ou un NOT_RUN.

## Correction bornée par le même agent

Sur FAIL ou NOT_RUN, corriger puis réexécuter
`bash e2e/agentic_loop.sh run 2` et `report 2`, puis au besoin
`run 3`. Le script refuse une tentative 4, la suppression
silencieuse d'anciennes preuves et la poursuite après PASS total.
Il n'appelle **aucun modèle** et ne modifie ni ne publie de code :
ce sont les décisions de l'agent développeur, puis de l'utilisateur.

Le test synthétique `python3 e2e/test_visual_diff.py` prouve que
l'alarme de comparaison de pixels détecte une obstruction volontaire ;
il ne vaut pas revue visuelle. Les références de comparaison
éventuelles sont placées dans `e2e/baselines/` uniquement lorsqu'elles
ont été approuvées. L'inspiration Eta/COUI est qualitative et ne
constitue pas une preuve de parité en l'absence d'une référence.

## Confidentialité

Seulement un **émulateur vierge** et du texte de test synthétique.
Ne pas automatiser l'Android physique de l'utilisateur, ne pas utiliser
ses comptes ni messages, ne pas envoyer d'images à une API distante,
ne pas versionner `e2e-artifacts/` ou des journaux privés.
